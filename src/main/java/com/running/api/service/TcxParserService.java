package com.running.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class TcxParserService {

    private static final double NO_DATA_ALTITUDE = -999.0;
    private static final double ELEVATION_NOISE_THRESHOLD = 0.01;
    private static final double MAX_VALID_SPEED = 20.0;
    private static final double HAVERSINE_MAX_DELTA = 100.0;
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private final XmlMapper xmlMapper = new XmlMapper();
    private final AnalyticsService analyticsService;

    @Value("${app.elevation.min-threshold-meters:0.1}")
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
        int maxCadence = 0;
        long sumCadence = 0;
        int countCadence = 0;
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

        double prevAlt = NO_DATA_ALTITUDE;
        double elevationGain = 0.0;
        double elevationLoss = 0.0;

        double runningTotalDistance = 0.0;
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

                Double lat = null;
                Double lon = null;
                JsonNode posNode = tp.path("Position");
                if (!posNode.isMissingNode() && posNode.has("LatitudeDegrees")) {
                    lat = posNode.path("LatitudeDegrees").asDouble();
                    lon = posNode.path("LongitudeDegrees").asDouble();
                }

                double rawDistance = tp.path("DistanceMeters").asDouble(-1.0);

                if (rawDistance >= 0.0) {
                    runningTotalDistance = rawDistance;
                } else if (lat != null && lon != null && prevPoint != null
                           && prevPoint.getLatitude() != null && prevPoint.getLongitude() != null) {
                    double deltaMeters = calculateHaversineDistance(
                        prevPoint.getLatitude(), prevPoint.getLongitude(),
                        lat, lon
                    );

                    if (deltaMeters > 0.0 && deltaMeters < HAVERSINE_MAX_DELTA) {
                        runningTotalDistance += deltaMeters;
                    }
                }

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

                int hr = tp.path("HeartRateBpm").path("Value").asInt(0);
                int cadence = tp.path("Cadence").asInt(0);

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

                double pointSpeed = extractSpeedFromExtensions(tp);

                if (pointSpeed == 0.0 && prevPoint != null && prevPoint.getDistanceMeters() != null) {
                    long dt = Duration.between(prevPoint.getTimestamp(), pointTime).getSeconds();
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

    private double extractSpeedFromExtensions(JsonNode tp) {
        JsonNode extensions = tp.path("Extensions");
        if (extensions.isMissingNode()) return 0.0;

        JsonNode tpx = extensions.has("TPX") ? extensions.path("TPX") : extensions.path("ns3:TPX");
        if (tpx.isMissingNode()) return 0.0;

        JsonNode speedNode = tpx.has("Speed") ? tpx.path("Speed") : tpx.path("ns3:Speed");
        return speedNode.asDouble(0.0);
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

    private double extractAltitude(JsonNode tp) {
        JsonNode node = tp.path("AltitudeMeters");
        if (node.isMissingNode()) node = tp.path("Position").path("AltitudeMeters");
        if (node.isMissingNode()) node = tp.path("Altitude");
        if (node.isMissingNode()) node = tp.path("ele");

        if (!node.isMissingNode() && !node.isNull()) {
            String txt = node.asText();
            if (!txt.isEmpty()) {
                try {
                    return Double.parseDouble(txt);
                } catch (NumberFormatException ignored) {
                    // Retornar valor nulo si el formato es inválido
                }
            }
        }
        return NO_DATA_ALTITUDE;
    }
}
