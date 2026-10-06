package com.running.api.service;

import com.running.api.dto.GeoJsonDto;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GeoService {

    private final ActivityRepository activityRepository;

    public GeoJsonDto exportGeoJson(String activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        List<List<Double>> coordinates = new ArrayList<>();
        for (ActivityTrackpoint pt : activity.getTrackpoints()) {
            if (pt.getLatitude() != null && pt.getLongitude() != null) {
                coordinates.add(List.of(pt.getLongitude(), pt.getLatitude(), pt.getAltitudeMeters() != null ? pt.getAltitudeMeters() : 0.0));
            }
        }

        GeoJsonDto.Geometry geometry = GeoJsonDto.Geometry.builder()
                .type("LineString")
                .coordinates(coordinates)
                .build();

        GeoJsonDto.GeoJsonFeature feature = GeoJsonDto.GeoJsonFeature.builder()
                .type("Feature")
                .geometry(geometry)
                .properties(Map.of(
                        "id", activity.getId(),
                        "name", activity.getName(),
                        "startTime", activity.getStartTime().toString(),
                        "distanceMeters", activity.getDistanceMeters()
                ))
                .build();

        return GeoJsonDto.builder()
                .type("FeatureCollection")
                .features(List.of(feature))
                .build();
    }

    public String exportGpx(String activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        StringBuilder gpx = new StringBuilder();
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"Running API\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        gpx.append("  <metadata>\n");
        gpx.append("    <name>").append(activity.getName()).append("</name>\n");
        gpx.append("    <time>").append(activity.getStartTime()).append("Z</time>\n");
        gpx.append("  </metadata>\n");
        gpx.append("  <trk>\n");
        gpx.append("    <name>").append(activity.getName()).append("</name>\n");
        gpx.append("    <trkseg>\n");

        for (ActivityTrackpoint pt : activity.getTrackpoints()) {
            if (pt.getLatitude() != null && pt.getLongitude() != null) {
                gpx.append(String.format(Locale.US, "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n", pt.getLatitude(), pt.getLongitude()));
                if (pt.getAltitudeMeters() != null) {
                    gpx.append(String.format(Locale.US, "        <ele>%.2f</ele>\n", pt.getAltitudeMeters()));
                }
                gpx.append("        <time>").append(pt.getTimestamp()).append("Z</time>\n");
                gpx.append("      </trkpt>\n");
            }
        }

        gpx.append("    </trkseg>\n");
        gpx.append("  </trk>\n");
        gpx.append("</gpx>");

        return gpx.toString();
    }
}
