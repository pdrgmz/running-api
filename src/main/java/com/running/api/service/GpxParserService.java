package com.running.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GpxParserService {

    private static final double NO_DATA_ALTITUDE = -999.0;
    private static final double ELEVATION_NOISE_THRESHOLD = 0.01;
    private static final double MAX_VALID_SPEED = 20.0;
    private static final double HAVERSINE_MAX_DELTA = 100.0;
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double MIN_VALID_SPEED_FOR_VO2 = 1.0; // m/s

    private final XmlMapper xmlMapper = new XmlMapper();
    private final AnalyticsService analyticsService;

    public Activity parse(InputStream inputStream) throws Exception {
        xmlMapper.configure(FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL, false);

        JsonNode root = xmlMapper.readTree(inputStream);

        // Con namespaces, XmlMapper genera nodos como "gpx" o "gpx.metadata" etc.
        // Buscar el nodo que contiene trackpoints o metadata
        JsonNode gpxNode = findGpxRoot(root);
        if (gpxNode == null) {
            throw new IllegalArgumentException("Estructura de archivo GPX no válida: Falta la etiqueta <gpx>.");
        }

        // Buscar metadata.time para el ID y startTime
        JsonNode metadata = findChild(gpxNode, "metadata");
        String rawId = null;
        if (metadata != null) {
            JsonNode timeNode = findChild(metadata, "time");
            if (timeNode != null) {
                rawId = timeNode.asText(null);
            }
        }
        if (rawId == null || rawId.isEmpty()) {
            rawId = "gpx_" + System.currentTimeMillis();
        }
        LocalDateTime startTime;
        try {
            startTime = ZonedDateTime.parse(rawId).toLocalDateTime();
        } catch (Exception e) {
            startTime = LocalDateTime.now();
        }

        double totalDistance = 0.0;
        double totalTime = 0.0;
        int maxHr = 0;
        long sumHr = 0;
        int countHr = 0;
        int maxCadence = 0;
        long sumCadence = 0;
        int countCadence = 0;
        double calculatedMaxSpeed = 0.0;

        Activity activity = Activity.builder()
                .id(rawId)
                .name("Carrera " + rawId.substring(0, Math.min(rawId.length(), 10)))
                .startTime(startTime)
                .build();

        double prevAlt = NO_DATA_ALTITUDE;
        double elevationGain = 0.0;
        double elevationLoss = 0.0;
        double runningTotalDistance = 0.0;
        ActivityTrackpoint prevPoint = null;

        // Buscar trk -> trkseg -> trkpt ignorando namespaces
        JsonNode trk = findChild(gpxNode, "trk");
        JsonNode trkseg = trk != null ? findChild(trk, "trkseg") : null;
        JsonNode trkpts = trkseg != null ? findChild(trkseg, "trkpt") : null;

        if (trkpts == null) {
            throw new IllegalArgumentException("Estructura de archivo GPX no válida: Falta <trk>/<trkseg>/<trkpt>.");
        }

        // Normalizar a array
        List<JsonNode> tpList = new ArrayList<>();
        if (trkpts.isArray()) {
            for (JsonNode tp : trkpts) tpList.add(tp);
        } else {
            tpList.add(trkpts);
        }

        for (JsonNode tp : tpList) {
            JsonNode timeNode = findChild(tp, "time");
            String timeStr = timeNode != null ? timeNode.asText(null) : null;
            if (timeStr == null || timeStr.isEmpty()) continue;

            LocalDateTime pointTime = ZonedDateTime.parse(timeStr).toLocalDateTime();

            // Atributos lat/lon del trkpt (XmlMapper los guarda como campos, no atributos)
            JsonNode latNode = findChild(tp, "lat");
            JsonNode lonNode = findChild(tp, "lon");
            Double lat = latNode != null ? latNode.asDouble() : null;
            Double lon = lonNode != null ? lonNode.asDouble() : null;

            double alt = extractAltitude(tp);

            if (alt != NO_DATA_ALTITUDE) {
                if (prevAlt != NO_DATA_ALTITUDE) {
                    double diff = alt - prevAlt;
                    if (Math.abs(diff) > ELEVATION_NOISE_THRESHOLD) {
                        if (diff > 0) elevationGain += diff;
                        else elevationLoss += Math.abs(diff);
                        prevAlt = alt;
                    }
                } else {
                    prevAlt = alt;
                }
            }

            int hr = extractHeartRate(tp);
            int cadence = extractCadence(tp);

            if (hr > 0) {
                if (hr > maxHr) maxHr = hr;
                sumHr += hr;
                countHr++;
            }

            if (cadence > 0) {
                if (cadence > maxCadence) maxCadence = cadence;
                sumCadence += cadence;
                countCadence++;
            }

            double pointSpeed = extractSpeed(tp);

            // Calcular distancia con Haversine si no hay campo distance
            if (lat != null && lon != null && prevPoint != null
                    && prevPoint.getLatitude() != null && prevPoint.getLongitude() != null) {
                double delta = calculateHaversineDistance(
                        prevPoint.getLatitude(), prevPoint.getLongitude(), lat, lon);
                if (delta > 0 && delta < HAVERSINE_MAX_DELTA) {
                    runningTotalDistance += delta;
                }
            }

            if (pointSpeed == 0.0 && prevPoint != null && prevPoint.getDistanceMeters() != null) {
                long dt = java.time.Duration.between(prevPoint.getTimestamp(), pointTime).getSeconds();
                double dd = runningTotalDistance - prevPoint.getDistanceMeters();
                if (dt > 0 && dd >= 0) {
                    pointSpeed = dd / dt;
                }
            }

            if (pointSpeed > calculatedMaxSpeed && pointSpeed < MAX_VALID_SPEED) {
                calculatedMaxSpeed = pointSpeed;
            }

            ActivityTrackpoint point = ActivityTrackpoint.builder()
                    .timestamp(pointTime)
                    .latitude(lat)
                    .longitude(lon)
                    .altitudeMeters(alt != NO_DATA_ALTITUDE ? alt : null)
                    .distanceMeters(runningTotalDistance)
                    .heartRate(hr > 0 ? hr : null)
                    .cadence(cadence > 0 ? cadence : null)
                    .speed(pointSpeed)
                    .build();

            activity.addTrackpoint(point);
            prevPoint = point;
        }

        // Asignar métricas calculadas siempre (nunca null si hay datos)
        activity.setDistanceMeters(runningTotalDistance > 0 ? runningTotalDistance : null);
        double totalTimeSec = calculateTotalTime(activity);
        activity.setTotalTimeSeconds(totalTimeSec > 0 ? totalTimeSec : null);
        activity.setAvgSpeed(totalTimeSec > 0 && runningTotalDistance > 0 ? runningTotalDistance / totalTimeSec : null);
        activity.setMaxSpeed(calculatedMaxSpeed > 0 ? calculatedMaxSpeed : null);
        activity.setMaxHeartRate(maxHr > 0 ? maxHr : null);
        activity.setAvgHeartRate(countHr > 0 ? (int) (sumHr / countHr) : null);
        activity.setAvgCadence(countCadence > 0 ? (int) Math.round((double) sumCadence / countCadence) : null);
        activity.setMaxCadence(maxCadence > 0 ? maxCadence : null);
        activity.setElevationGain(elevationGain > 0 ? elevationGain : null);
        activity.setElevationLoss(elevationLoss > 0 ? elevationLoss : null);

        // --- CÁLCULO DE TRAINING LOAD (TRIMP simplificado) ---
        calculateTrainingLoad(activity, countHr, sumHr, maxHr);

        // --- CÁLCULO DE VAM (Velocidad Aeróbica Máxima) ---
        calculateVam(activity, calculatedMaxSpeed);

        // --- CÁLCULO DE VO2MAX ESTIMADO ---
        calculateVo2Max(activity, calculatedMaxSpeed);

        if (analyticsService != null) {
            analyticsService.populateMetrics(activity);
        }

        return activity;
    }

    /**
     * Detecta si la actividad es una carrera basándose en el elemento <type> dentro de <trk>.
     * @return true si es running, false si es otro deporte (cycling, walking, etc.)
     */
    public static boolean isRunningActivity(InputStream inputStream) {
        try {
            JsonNode root = new XmlMapper().readTree(inputStream);
            JsonNode gpxNode = findGpxRoot(root);
            if (gpxNode == null) return false;
            JsonNode trk = findChild(gpxNode, "trk");
            if (trk == null) return false;
            JsonNode typeNode = findChild(trk, "type");
            if (typeNode == null) return false;
            String type = typeNode.asText("").toLowerCase();
            return type.contains("running") || type.contains("run") || type.contains("carrera");
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode findFirstChild(JsonNode parent) {
        if (parent == null || !parent.isObject()) return null;
        Iterator<String> it = parent.fieldNames();
        if (it.hasNext()) {
            return parent.get(it.next());
        }
        return null;
    }

    private static JsonNode findChild(JsonNode parent, String name) {
        if (parent == null || !parent.isObject()) return null;
        Iterator<String> it = parent.fieldNames();
        while (it.hasNext()) {
            String fieldName = it.next();
            if (fieldName.equals(name) || fieldName.endsWith(":" + name)) {
                return parent.get(fieldName);
            }
        }
        return null;
    }

    private static JsonNode findGpxRoot(JsonNode root) {
        if (root == null || !root.isObject()) return null;

        // Caso 1: root tiene "gpx" como campo
        Iterator<String> rootFields = root.fieldNames();
        while (rootFields.hasNext()) {
            String field = rootFields.next();
            if (field.equals("gpx") || field.endsWith(":gpx")) {
                return root.get(field);
            }
        }

        // Caso 2: root tiene metadata y trk directamente (namespace por defecto)
        if (findChild(root, "metadata") != null || findChild(root, "trk") != null) {
            return root;
        }

        // Caso 3: buscar primer campo que sea un objeto con trk o metadata
        rootFields = root.fieldNames();
        while (rootFields.hasNext()) {
            String field = rootFields.next();
            JsonNode child = root.get(field);
            if (child != null && child.isObject()) {
                if (findChild(child, "metadata") != null || findChild(child, "trk") != null) {
                    return child;
                }
            }
        }

        return null;
    }

    private double calculateTotalTime(Activity activity) {
        List<ActivityTrackpoint> tps = activity.getTrackpoints();
        if (tps == null || tps.size() < 2) return 0.0;
        LocalDateTime first = tps.get(0).getTimestamp();
        LocalDateTime last = tps.get(tps.size() - 1).getTimestamp();
        if (first != null && last != null) {
            return java.time.Duration.between(first, last).getSeconds();
        }
        return 0.0;
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Training Load - TRIMP simplificado de Banister:
     * TL = duración(min) * (HRavg / HRmax)
     */
    private void calculateTrainingLoad(Activity activity, int countHr, long sumHr, int maxHr) {
        if (activity.getTotalTimeSeconds() != null && activity.getTotalTimeSeconds() > 0
                && countHr > 0 && maxHr > 0) {
            double durationMin = activity.getTotalTimeSeconds() / 60.0;
            double hrAvg = (double) sumHr / countHr;
            if (maxHr > hrAvg) {
                double tl = durationMin * (hrAvg / maxHr);
                activity.setTrainingLoad(Math.round(tl * 10.0) / 10.0);
            }
        }
    }

    /**
     * VAM (Velocidad Aeróbica Máxima) - estimación desde velocidad máxima y pendiente.
     */
    private void calculateVam(Activity activity, double maxSpeedMs) {
        if (maxSpeedMs > 0) {
            double maxSpeedKmh = maxSpeedMs * 3.6;

            double avgGradient = 0;
            if (activity.getDistanceMeters() != null && activity.getDistanceMeters() > 0
                    && activity.getElevationGain() != null && activity.getElevationGain() > 0) {
                avgGradient = activity.getElevationGain() / activity.getDistanceMeters();
            }

            double vam = maxSpeedKmh * (1 + Math.max(avgGradient, 0) * 0.1);
            activity.setVamKmH(Math.round(vam * 10.0) / 10.0);
        }
    }

    /**
     * VO2Max estimado usando la fórmula de Daniels-Gilbert:
     * VO2 = -4.60 + 0.182258*v + 0.000104*v^2
     * donde v = velocidad en m/min
     */
    private void calculateVo2Max(Activity activity, double maxSpeedMs) {
        if (maxSpeedMs > MIN_VALID_SPEED_FOR_VO2) {
            double v = maxSpeedMs * 60.0; // m/min
            double vo2 = -4.60 + 0.182258 * v + 0.000104 * v * v;

            if (vo2 > 20 && vo2 < 100) { // Rango fisiológico válido
                activity.setVo2MaxEstimated(Math.round(vo2 * 10.0) / 10.0);
            }
        }
    }

    private double extractAltitude(JsonNode tp) {
        JsonNode node = findChild(tp, "ele");
        if (node != null) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return NO_DATA_ALTITUDE;
    }

    private int extractHeartRate(JsonNode tp) {
        JsonNode extensions = findChild(tp, "extensions");
        if (extensions == null) return 0;

        // Buscar TrackPointExtension con o sin namespace
        JsonNode tpx = null;
        Iterator<String> it = extensions.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (name.equals("gpxtpx:TrackPointExtension") || name.equals("TrackPointExtension")) {
                tpx = extensions.get(name);
                break;
            }
        }
        if (tpx == null) return 0;

        JsonNode hrNode = null;
        it = tpx.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (name.equals("gpxtpx:hr") || name.equals("hr")) {
                hrNode = tpx.get(name);
                break;
            }
        }
        return hrNode != null ? hrNode.asInt(0) : 0;
    }

    private int extractCadence(JsonNode tp) {
        JsonNode extensions = findChild(tp, "extensions");
        if (extensions == null) return 0;

        JsonNode tpx = null;
        Iterator<String> it = extensions.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (name.equals("gpxtpx:TrackPointExtension") || name.equals("TrackPointExtension")) {
                tpx = extensions.get(name);
                break;
            }
        }
        if (tpx == null) return 0;

        JsonNode cadNode = null;
        it = tpx.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (name.equals("gpxtpx:cad") || name.equals("cad")) {
                cadNode = tpx.get(name);
                break;
            }
        }
        return cadNode != null ? cadNode.asInt(0) : 0;
    }

    private double extractSpeed(JsonNode tp) {
        JsonNode extensions = findChild(tp, "extensions");
        if (extensions == null) return 0.0;

        JsonNode speedNode = findChild(extensions, "speed");
        return speedNode != null ? speedNode.asDouble(0.0) : 0.0;
    }
}
