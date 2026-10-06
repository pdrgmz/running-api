package com.running.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Service
public class TcxParserService {

    private final XmlMapper xmlMapper = new XmlMapper();

    @Value("${app.elevation.min-threshold-meters:0.8}")
    private double elevationThreshold;

    public Activity parse(InputStream inputStream) throws Exception {
        
    JsonNode root = xmlMapper.readTree(inputStream);
    JsonNode activityNode = root.path("Activities").path("Activity");

    if (activityNode.isMissingNode()) {
        throw new IllegalArgumentException("Estructura de archivo TCX no válida: Falta la etiqueta <Activity>.");
    }

    String rawId = activityNode.path("Id").asText();
    LocalDateTime startTime = ZonedDateTime.parse(rawId).toLocalDateTime();

    double totalDistance = 0.0;
    double totalTime = 0.0;
    int totalCalories = 0;
    int maxHr = 0;
    long sumHr = 0;
    int countHr = 0;
    double calculatedMaxSpeed = 0.0;

    Activity activity = Activity.builder()
            .id(rawId)
            .name("Carrera " + rawId.substring(0, Math.min(rawId.length(), 10)))
            .startTime(startTime)
            .build();

    JsonNode laps = activityNode.path("Lap");
    if (!laps.isArray()) {
        laps = xmlMapper.createArrayNode().add(laps);
    }

    double prevAlt = -999.0;
    double elevationGain = 0.0;
    double elevationLoss = 0.0;
    
    double runningTotalDistance = 0.0; // Distancia acumulada sumada punto a punto
    ActivityTrackpoint prevPoint = null;

    for (JsonNode lap : laps) {
        totalDistance += lap.path("DistanceMeters").asDouble(0.0);
        totalTime += lap.path("TotalTimeSeconds").asDouble(0.0);
        totalCalories += lap.path("Calories").asInt(0);

        JsonNode trackpoints = lap.path("Track").path("Trackpoint");
        if (!trackpoints.isArray()) {
            trackpoints = xmlMapper.createArrayNode().add(trackpoints);
        }

        for (JsonNode tp : trackpoints) {
            String timeStr = tp.path("Time").asText(null);
            if (timeStr == null || timeStr.isEmpty()) continue;

            LocalDateTime pointTime = ZonedDateTime.parse(timeStr).toLocalDateTime();

            // 1. Coordenadas GPS (Opcionales: pueden ser nulas sin descarte de punto)
            Double lat = null;
            Double lon = null;
            JsonNode posNode = tp.path("Position");
            if (!posNode.isMissingNode() && posNode.has("LatitudeDegrees")) {
                lat = posNode.path("LatitudeDegrees").asDouble();
                lon = posNode.path("LongitudeDegrees").asDouble();
            }

            // 2. Distancia Acumulada Punto a Punto
            double rawDistance = tp.path("DistanceMeters").asDouble(-1.0);

            if (rawDistance >= 0.0) {
                // Opción A: Usar la distancia acumulada leída directamente del XML
                runningTotalDistance = rawDistance;
            } else if (lat != null && lon != null && prevPoint != null 
                       && prevPoint.getLatitude() != null && prevPoint.getLongitude() != null) {
                
                // Opción B: Fallback - Calcular delta por Haversine si no viene DistanceMeters
                double deltaMeters = calculateHaversineDistance(
                    prevPoint.getLatitude(), prevPoint.getLongitude(),
                    lat, lon
                );

                if (deltaMeters > 0.0 && deltaMeters < 100.0) { // Filtro de ruido
                    runningTotalDistance += deltaMeters;
                }
            }

            // 3. Métrica de Elevación con Umbral
            double alt = tp.path("AltitudeMeters").asDouble(-999.0);
            if (alt != -999.0) {
                if (prevAlt != -999.0) {
                    double diff = alt - prevAlt;
                    if (Math.abs(diff) >= elevationThreshold) {
                        if (diff > 0) elevationGain += diff;
                        else elevationLoss += Math.abs(diff);
                        prevAlt = alt;
                    }
                } else {
                    prevAlt = alt;
                }
            }

            // 4. Frecuencia Cardíaca y Cadencia
            int hr = tp.path("HeartRateBpm").path("Value").asInt(0);
            int cadence = tp.path("Cadence").asInt(0);

            if (hr > 0) {
                if (hr > maxHr) maxHr = hr;
                sumHr += hr;
                countHr++;
            }

            // 5. Velocidad (Extensión ns3:TPX o cálculo por delta)
            double pointSpeed = extractSpeedFromExtensions(tp);

            if (pointSpeed == 0.0 && prevPoint != null && prevPoint.getDistanceMeters() != null) {
                long dt = Duration.between(prevPoint.getTimestamp(), pointTime).getSeconds();
                double dd = runningTotalDistance - prevPoint.getDistanceMeters();
                if (dt > 0 && dd >= 0) {
                    pointSpeed = dd / dt;
                }
            }

            if (pointSpeed > calculatedMaxSpeed && pointSpeed < 20.0) { // < 72 km/h
                calculatedMaxSpeed = pointSpeed;
            }

            // 6. Construcción del Trackpoint
            ActivityTrackpoint point = ActivityTrackpoint.builder()
                    .timestamp(pointTime)
                    .latitude(lat)
                    .longitude(lon)
                    .altitudeMeters(alt != -999.0 ? alt : null)
                    .distanceMeters(runningTotalDistance) // <-- Distancia sumada acumulada
                    .heartRate(hr > 0 ? hr : null)
                    .cadence(cadence > 0 ? cadence : null)
                    .speed(pointSpeed)
                    .build();

            activity.addTrackpoint(point);
            prevPoint = point;
        }
    }

    activity.setDistanceMeters(totalDistance > 0 ? totalDistance : runningTotalDistance);
    activity.setTotalTimeSeconds(totalTime);
    activity.setAvgSpeed(totalTime > 0 ? activity.getDistanceMeters() / totalTime : 0.0);
    activity.setMaxSpeed(calculatedMaxSpeed > 0 ? calculatedMaxSpeed : activity.getAvgSpeed());
    activity.setMaxHeartRate(maxHr > 0 ? maxHr : null);
    activity.setAvgHeartRate(countHr > 0 ? (int) (sumHr / countHr) : null);
    activity.setTotalCalories(totalCalories);
    activity.setElevationGain(elevationGain);
    activity.setElevationLoss(elevationLoss);

    return activity;
}

/**
 * Método auxiliar para extraera velocidad del nodo <Extensions> (<ns3:TPX><ns3:Speed>).
 */
private double extractSpeedFromExtensions(JsonNode tp) {
    JsonNode extensions = tp.path("Extensions");
    if (extensions.isMissingNode()) return 0.0;

    JsonNode tpx = extensions.has("TPX") ? extensions.path("TPX") : extensions.path("ns3:TPX");
    if (tpx.isMissingNode()) return 0.0;

    JsonNode speedNode = tpx.has("Speed") ? tpx.path("Speed") : tpx.path("ns3:Speed");
    return speedNode.asDouble(0.0);
}

/**
 * Método auxiliar para calcular la distancia en metros entre dos coordenadas (Fórmula del Haversine).
 */
private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
    final double EARTH_RADIUS_METERS = 6371000.0;
    
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);

    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
               Math.sin(dLon / 2) * Math.sin(dLon / 2);

    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return EARTH_RADIUS_METERS * c;
}
}
