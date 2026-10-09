package com.running.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GpxParserService {

    private static final double NO_DATA_ALTITUDE = -999.0;
    private static final double ELEVATION_NOISE_THRESHOLD = 0.01;
    private static final double MAX_VALID_SPEED = 20.0;
    private static final double HAVERSINE_MAX_DELTA = 100.0;
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private final XmlMapper xmlMapper = new XmlMapper();
    private final AnalyticsService analyticsService;

    public Activity parse(InputStream inputStream) throws Exception {
        JsonNode root = xmlMapper.readTree(inputStream);
        JsonNode gpxNode = root.path("gpx");

        if (gpxNode.isMissingNode()) {
            throw new IllegalArgumentException("Estructura de archivo GPX no válida: Falta la etiqueta <gpx>.");
        }

        String rawId = gpxNode.path("metadata").path("time").asText();
        if (rawId.isEmpty()) {
            rawId = "gpx_" + System.currentTimeMillis();
        }
        LocalDateTime startTime = ZonedDateTime.parse(rawId).toLocalDateTime();

        double totalDistance = 0.0;
        double totalTime = 0.0;
        int totalCalories = 0;
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

        JsonNode trkpts = gpxNode.path("trk").path("trkseg").path("trkpt");
        if (!trkpts.isArray()) {
            trkpts = xmlMapper.createArrayNode().add(trkpts);
        }

        for (JsonNode tp : trkpts) {
            String timeStr = tp.path("time").asText(null);
            if (timeStr == null || timeStr.isEmpty()) continue;

            LocalDateTime pointTime = ZonedDateTime.parse(timeStr).toLocalDateTime();

            Double lat = tp.has("lat") ? tp.path("lat").asDouble() : null;
            Double lon = tp.has("lon") ? tp.path("lon").asDouble() : null;

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

        activity.setDistanceMeters(totalDistance > 0 ? totalDistance : runningTotalDistance);
        activity.setTotalTimeSeconds(totalTime);
        activity.setAvgSpeed(totalTime > 0 ? activity.getDistanceMeters() / totalTime : 0.0);
        activity.setMaxSpeed(calculatedMaxSpeed > 0 ? calculatedMaxSpeed : activity.getAvgSpeed());
        activity.setMaxHeartRate(maxHr > 0 ? maxHr : null);
        activity.setAvgHeartRate(countHr > 0 ? (int) (sumHr / countHr) : null);
        activity.setAvgCadence(countCadence > 0 ? (int) Math.round((double) sumCadence / countCadence) : null);
        activity.setMaxCadence(maxCadence > 0 ? maxCadence : null);
        activity.setTotalCalories(totalCalories);
        activity.setElevationGain(elevationGain);
        activity.setElevationLoss(elevationLoss);

        if (analyticsService != null) {
            analyticsService.populateMetrics(activity);
        }

        return activity;
    }

    private double extractAltitude(JsonNode tp) {
        JsonNode node = tp.path("ele");
        if (!node.isMissingNode() && !node.isNull()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return NO_DATA_ALTITUDE;
    }

    private int extractHeartRate(JsonNode tp) {
        JsonNode extensions = tp.path("extensions");
        if (extensions.isMissingNode()) return 0;

        JsonNode hrNode = extensions.path("gpxtpx:TrackPointExtension").path("gpxtpx:hr");
        if (hrNode.isMissingNode()) {
            hrNode = extensions.path("hr");
        }
        return hrNode.asInt(0);
    }

    private int extractCadence(JsonNode tp) {
        JsonNode extensions = tp.path("extensions");
        if (extensions.isMissingNode()) return 0;

        JsonNode cadNode = extensions.path("gpxtpx:TrackPointExtension").path("gpxtpx:cad");
        if (cadNode.isMissingNode()) {
            cadNode = extensions.path("cad");
        }
        return cadNode.asInt(0);
    }

    private double extractSpeed(JsonNode tp) {
        JsonNode extensions = tp.path("extensions");
        if (extensions.isMissingNode()) return 0.0;

        JsonNode speedNode = extensions.path("speed");
        return speedNode.asDouble(0.0);
    }
}
