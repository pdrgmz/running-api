package com.running.api.service;

import com.running.api.dto.GeoJsonDto;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoService {

    private final ActivityRepository activityRepository;
    private final ActivityTrackpointRepository trackpointRepository;

    @Async("taskExecutor")
    public CompletableFuture<GeoJsonDto> exportGeoJsonAsync(String activityId) {
        return CompletableFuture.completedFuture(exportGeoJson(activityId));
    }

    @Async("taskExecutor")
    public CompletableFuture<String> exportGpxAsync(String activityId) {
        return CompletableFuture.completedFuture(exportGpx(activityId));
    }

    @Transactional(readOnly = true)
    public GeoJsonDto exportGeoJson(String activityId) {
        log.debug("[GEO] Exportando GeoJSON para actividad: {}", activityId);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        List<ActivityTrackpoint> trackpoints = trackpointRepository.findByActivityIdOrderByTimestampAsc(activityId);

        List<List<Double>> coordinates = new ArrayList<>();
        for (ActivityTrackpoint pt : trackpoints) {
            if (pt.getLatitude() != null && pt.getLongitude() != null) {
                coordinates.add(List.of(
                        pt.getLongitude(),
                        pt.getLatitude(),
                        pt.getAltitudeMeters() != null ? pt.getAltitudeMeters() : 0.0
                ));
            }
        }

        log.debug("[GEO] GeoJSON generado para {} con {} coordenadas", activityId, coordinates.size());

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

    @Transactional(readOnly = true)
    public String exportGpx(String activityId) {
        log.debug("[GEO] Exportando GPX para actividad: {}", activityId);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrera no encontrada con id: " + activityId));

        List<ActivityTrackpoint> trackpoints = trackpointRepository.findByActivityIdOrderByTimestampAsc(activityId);

        StringBuilder gpx = new StringBuilder(1024);
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"Running API\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        gpx.append("  <metadata>\n");
        gpx.append("    <name>").append(activity.getName()).append("</name>\n");
        gpx.append("    <time>").append(activity.getStartTime()).append("Z</time>\n");
        gpx.append("  </metadata>\n");
        gpx.append("  <trk>\n");
        gpx.append("    <name>").append(activity.getName()).append("</name>\n");
        gpx.append("    <trkseg>\n");

        int exportedPoints = 0;
        for (ActivityTrackpoint pt : trackpoints) {
            if (pt.getLatitude() != null && pt.getLongitude() != null) {
                gpx.append(String.format(Locale.US, "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n", pt.getLatitude(), pt.getLongitude()));
                if (pt.getAltitudeMeters() != null) {
                    gpx.append(String.format(Locale.US, "        <ele>%.2f</ele>\n", pt.getAltitudeMeters()));
                }
                gpx.append("        <time>").append(pt.getTimestamp()).append("Z</time>\n");
                gpx.append("      </trkpt>\n");
                exportedPoints++;
            }
        }

        gpx.append("    </trkseg>\n");
        gpx.append("  </trk>\n");
        gpx.append("</gpx>");

        log.debug("[GEO] GPX generado para {} con {} puntos exportados", activityId, exportedPoints);
        return gpx.toString();
    }
}
