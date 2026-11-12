package com.running.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.exception.GenerationException;
import com.running.api.exception.GenerationNotConfiguredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente del webhook de n8n que arranca la generación de la recomendación
 * diaria.
 * <p>
 * El workflow de n8n es agnóstico al usuario: recibe un JWT efímero en la
 * cabecera {@code Authorization} y lo reenvía al servidor MCP. La API no envía
 * ni guarda credenciales de usuario en n8n.
 */
@Slf4j
@Component
public class N8nDailyWebhookClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String webhookUrl;
    private final String mcpUrl;
    private final Duration requestTimeout;

    public N8nDailyWebhookClient(
            ObjectMapper objectMapper,
            @Value("${app.training.daily.n8n-webhook-url:}") String webhookUrl,
            @Value("${app.training.daily.mcp-url:http://localhost:8080/mcp}") String mcpUrl,
            @Value("${app.training.daily.webhook-timeout-seconds:20}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.mcpUrl = mcpUrl == null ? "" : mcpUrl.trim();
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isConfigured() {
        return !webhookUrl.isBlank();
    }

    public void trigger(String token, String requestId, LocalDate date, boolean replace) {
        if (!isConfigured()) {
            throw new GenerationNotConfiguredException(
                    "El webhook de n8n no está configurado (APP_TRAINING_DAILY_N8N_WEBHOOK_URL)");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("date", date.toString());
        body.put("replace", replace);
        body.put("mcpUrl", mcpUrl);

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Request-Id", requestId)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GenerationException(
                        "El webhook de n8n respondió con HTTP " + response.statusCode());
            }
            log.info("Job de recomendación enviado a n8n: requestId={}, date={}, httpStatus={}",
                    requestId, date, response.statusCode());
        } catch (GenerationException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GenerationException("Petición al webhook de n8n interrumpida", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new GenerationException("No se pudo llamar al webhook de n8n: " + ex.getMessage(), ex);
        }
    }
}
