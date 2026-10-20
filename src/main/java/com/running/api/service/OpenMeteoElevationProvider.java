package com.running.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Obtiene altitudes reales desde la API pública de elevación de Open-Meteo
 * (<a href="https://open-meteo.com/en/docs/elevation-api">Open-Meteo Elevation API</a>),
 * basada en el modelo digital de elevaciones Copernicus DEM (~90 m).
 * <p>
 * El plan gratuito limita a 600 llamadas por minuto y una petición con varias coordenadas
 * cuenta como una llamada por coordenada. Por eso se aplica una ventana deslizante
 * configurable ({@code app.elevation.max-calls-per-minute}) y, si aun así llega un HTTP 429,
 * se respeta la cabecera {@code Retry-After} y se reintenta con backoff.
 * <p>
 * Si la consulta termina fallando (sin red, límite persistente, etc.) se devuelven
 * {@code null} y el proceso de completado continúa sin romper la importación.
 */
@Slf4j
@Service
public class OpenMeteoElevationProvider implements ElevationProvider {

    private static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final long MAX_BODY_LOG_LENGTH = 300L;
    private static final String FREE_API_HOST = "https://api.open-meteo.com/";
    private static final String CUSTOMER_API_HOST = "https://customer-api.open-meteo.com/";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String apiUrl;
    private final String apiKey;
    private final int batchSize;
    private final Duration timeout;
    private final long minIntervalMillis;
    private final int maxCallsPerMinute;
    private final int maxRetries;
    private final long retryBackoffMillis;
    private final long maxRetryAfterMillis;

    private final Map<String, Double> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastRequestAt = new AtomicLong(0);
    private final Deque<CallRecord> callWindow = new ArrayDeque<>();
    private final Object rateLock = new Object();

    public OpenMeteoElevationProvider(
            ObjectMapper objectMapper,
            @Value("${app.elevation.external-enabled:true}") boolean enabled,
            @Value("${app.elevation.api-url:https://api.open-meteo.com/v1/elevation}") String apiUrl,
            @Value("${app.elevation.api-key:}") String apiKey,
            @Value("${app.elevation.batch-size:50}") int batchSize,
            @Value("${app.elevation.timeout-seconds:10}") int timeoutSeconds,
            @Value("${app.elevation.min-interval-millis:100}") long minIntervalMillis,
            @Value("${app.elevation.max-calls-per-minute:500}") int maxCallsPerMinute,
            @Value("${app.elevation.max-retries:2}") int maxRetries,
            @Value("${app.elevation.retry-backoff-millis:2000}") long retryBackoffMillis,
            @Value("${app.elevation.max-retry-after-seconds:60}") int maxRetryAfterSeconds) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiUrl = resolveApiUrl(apiUrl, this.apiKey);
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.minIntervalMillis = Math.max(0, minIntervalMillis);
        this.maxCallsPerMinute = Math.max(0, maxCallsPerMinute);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffMillis = Math.max(0, retryBackoffMillis);
        this.maxRetryAfterMillis = Math.max(1, maxRetryAfterSeconds) * 1000L;
        this.batchSize = Math.max(1, this.maxCallsPerMinute > 0
                ? Math.min(batchSize, this.maxCallsPerMinute)
                : batchSize);
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        if (!this.apiKey.isEmpty()) {
            log.info("Open-Meteo: API key configurada; usando {}", this.apiUrl);
        }
    }

    /**
     * Si hay API key y se está usando el endpoint gratuito por defecto, se cambia al
     * endpoint de cliente de Open-Meteo. Así basta con definir {@code APP_ELEVATION_API_KEY}
     * sin tocar la URL.
     */
    static String resolveApiUrl(String apiUrl, String apiKey) {
        String resolved = (apiUrl == null || apiUrl.isBlank())
                ? "https://api.open-meteo.com/v1/elevation"
                : apiUrl;
        if (apiKey != null && !apiKey.isBlank() && resolved.startsWith(FREE_API_HOST)) {
            return CUSTOMER_API_HOST + resolved.substring(FREE_API_HOST.length());
        }
        return resolved;
    }

    @Override
    public List<Double> lookup(List<Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return List.of();
        }

        List<Double> result = new ArrayList<>(Collections.nCopies(coordinates.size(), null));
        if (!enabled) {
            return result;
        }

        List<Integer> missingIndexes = new ArrayList<>();
        List<Coordinate> missingCoordinates = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i++) {
            Coordinate coordinate = coordinates.get(i);
            if (coordinate == null || !isValid(coordinate)) {
                continue;
            }
            Double cached = cache.get(cacheKey(coordinate));
            if (cached != null) {
                result.set(i, cached);
            } else {
                missingIndexes.add(i);
                missingCoordinates.add(coordinate);
            }
        }

        for (int from = 0; from < missingCoordinates.size(); from += batchSize) {
            int to = Math.min(from + batchSize, missingCoordinates.size());
            List<Coordinate> batch = missingCoordinates.subList(from, to);
            List<Double> elevations = requestBatch(batch);

            if (elevations.isEmpty()) {
                // No se insiste con el resto del lote: evita amplificar un 429 o una caída.
                log.warn("Se detiene la consulta de elevación tras fallar un lote de {} coordenadas", batch.size());
                break;
            }

            for (int i = 0; i < batch.size() && i < elevations.size(); i++) {
                Double elevation = elevations.get(i);
                if (elevation == null || !Double.isFinite(elevation)) {
                    continue;
                }
                result.set(missingIndexes.get(from + i), elevation);
                cache.put(cacheKey(batch.get(i)), elevation);
            }
        }

        return result;
    }

    private List<Double> requestBatch(List<Coordinate> batch) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                throttle();
                acquireCallBudget(batch.size());

                HttpRequest request = HttpRequest.newBuilder(URI.create(buildUrl(batch)))
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    return parseElevations(response.body());
                }

                if (status == 429 || status >= 500) {
                    if (attempt < maxRetries) {
                        long waitMillis = retryDelayMillis(response, attempt);
                        log.warn("Open-Meteo respondió {}; reintento {}/{} en {} ms",
                                status, attempt + 1, maxRetries, waitMillis);
                        sleep(waitMillis);
                        continue;
                    }
                    log.warn("Open-Meteo respondió {} tras {} intentos; se omite la altitud de {} coordenadas",
                            status, attempt + 1, batch.size());
                    return List.of();
                }

                log.warn("Open-Meteo respondió {}: {}", status, abbreviate(redact(response.body())));
                return List.of();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Consulta de elevación interrumpida: {}", redact(e.getMessage()));
                return List.of();
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    long waitMillis = retryBackoffMillis * (attempt + 1);
                    log.warn("Error consultando Open-Meteo ({}); reintento {}/{} en {} ms",
                            redact(e.getMessage()), attempt + 1, maxRetries, waitMillis);
                    try {
                        sleep(waitMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return List.of();
                    }
                } else {
                    log.warn("No se pudo obtener la elevación desde Open-Meteo: {}", redact(e.getMessage()));
                    return List.of();
                }
            }
        }
        return List.of();
    }

    private List<Double> parseElevations(String body) {
        try {
            JsonNode elevationNode = objectMapper.readTree(body).path("elevation");
            if (!elevationNode.isArray()) {
                log.warn("Respuesta inesperada de Open-Meteo: {}", abbreviate(redact(body)));
                return List.of();
            }

            List<Double> elevations = new ArrayList<>(elevationNode.size());
            for (JsonNode node : elevationNode) {
                elevations.add(node.isNumber() ? node.asDouble() : null);
            }
            return elevations;
        } catch (Exception e) {
            log.warn("No se pudo interpretar la respuesta de Open-Meteo: {}", redact(e.getMessage()));
            return List.of();
        }
    }

    private String buildUrl(List<Coordinate> batch) {
        StringBuilder latitudes = new StringBuilder(batch.size() * 10);
        StringBuilder longitudes = new StringBuilder(batch.size() * 10);
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) {
                latitudes.append(',');
                longitudes.append(',');
            }
            Coordinate coordinate = batch.get(i);
            latitudes.append(String.format(Locale.US, "%.6f", coordinate.latitude()));
            longitudes.append(String.format(Locale.US, "%.6f", coordinate.longitude()));
        }

        String separator = apiUrl.contains("?") ? "&" : "?";
        StringBuilder url = new StringBuilder(apiUrl)
                .append(separator)
                .append("latitude=").append(latitudes)
                .append("&longitude=").append(longitudes);

        if (!apiKey.isEmpty() && !apiUrl.toLowerCase(Locale.ROOT).contains("apikey=")) {
            url.append("&apikey=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    /**
     * Ventana deslizante de 60 s. Se contabiliza una llamada por coordenada, que es como
     * Open-Meteo mide el uso de la Elevation API.
     */
    private void acquireCallBudget(int calls) throws InterruptedException {
        if (maxCallsPerMinute <= 0 || calls <= 0) {
            return;
        }

        int needed = Math.min(calls, maxCallsPerMinute);
        while (true) {
            long waitMillis;
            synchronized (rateLock) {
                long now = System.currentTimeMillis();
                while (!callWindow.isEmpty()
                        && now - callWindow.peekFirst().timestamp() >= RATE_WINDOW_MILLIS) {
                    callWindow.removeFirst();
                }

                int usedCalls = 0;
                for (CallRecord record : callWindow) {
                    usedCalls += record.calls();
                }

                if (usedCalls + needed <= maxCallsPerMinute) {
                    callWindow.addLast(new CallRecord(now, needed));
                    return;
                }

                waitMillis = RATE_WINDOW_MILLIS
                        - (now - callWindow.peekFirst().timestamp()) + 50;
            }

            if (waitMillis > 0) {
                log.debug("Esperando {} ms para respetar el límite de Open-Meteo", waitMillis);
                Thread.sleep(waitMillis);
            }
        }
    }

    private void throttle() throws InterruptedException {
        if (minIntervalMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastRequestAt.getAndSet(now);
        long waitMillis = minIntervalMillis - (now - previous);
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
    }

    private long retryDelayMillis(HttpResponse<String> response, int attempt) {
        Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            Long parsed = parseRetryAfterMillis(retryAfter.get());
            if (parsed != null) {
                return Math.min(parsed, maxRetryAfterMillis);
            }
        }
        long backoff = retryBackoffMillis * (attempt + 1L);
        return Math.min(backoff, maxRetryAfterMillis);
    }

    private static Long parseRetryAfterMillis(String value) {
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return Math.max(0L, seconds * 1000L);
        } catch (NumberFormatException ignored) {
            // Puede ser una fecha HTTP.
        }
        try {
            ZonedDateTime date = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Math.max(0L, Duration.between(ZonedDateTime.now(), date).toMillis());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_BODY_LOG_LENGTH
                ? body
                : body.substring(0, (int) MAX_BODY_LOG_LENGTH) + "...";
    }

    private String redact(String text) {
        if (text == null || apiKey.isEmpty()) {
            return text;
        }
        return text.replace(apiKey, "***");
    }

    private static boolean isValid(Coordinate coordinate) {
        return Double.isFinite(coordinate.latitude()) && Double.isFinite(coordinate.longitude())
                && coordinate.latitude() >= -90.0 && coordinate.latitude() <= 90.0
                && coordinate.longitude() >= -180.0 && coordinate.longitude() <= 180.0;
    }

    private static String cacheKey(Coordinate coordinate) {
        return String.format(Locale.US, "%.5f,%.5f", coordinate.latitude(), coordinate.longitude());
    }

    private record CallRecord(long timestamp, int calls) {
    }
}
