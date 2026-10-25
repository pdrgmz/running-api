package com.running.api.service;

import com.running.api.dto.SplitDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private static final Long USER_ID = 1L;
    private static final String ACTIVITY_ID = "activity-1";

    @Test
    void calculatesTenKilometerSplitsWhenTrackpointsComeOutOfOrder() {
        // 10 km a 2,5 m/s: una muestra cada 10 m y 4 s. La lista llega rotada, con el último
        // trackpoint primero, igual que ocurría al leer la colección JPA sin ORDER BY.
        List<ActivityTrackpoint> points = buildSeries(1000, 10.0, 4);
        List<ActivityTrackpoint> rotated = new ArrayList<>(points.size());
        rotated.add(points.get(points.size() - 1));
        rotated.addAll(points.subList(0, points.size() - 1));

        AnalyticsService service = serviceWithActivity(10_000.0, rotated);

        List<SplitDto> splits = service.calculateSplits(USER_ID, ACTIVITY_ID);

        assertThat(splits).hasSize(10);
        assertThat(splits).extracting(SplitDto::getKm).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(splits).allSatisfy(split -> {
            assertThat(split.getDistanceMeters()).isEqualTo(1000.0);
            assertThat(split.getTimeSeconds()).isEqualTo(400L);
            assertThat(split.getAvgHeartRate()).isEqualTo(150);
        });
        assertThat(splits.stream().mapToDouble(SplitDto::getDistanceMeters).sum()).isEqualTo(10_000.0);
    }

    @Test
    void scalesTrackpointDistanceToActivityDistanceWhenSeriesIsShort() {
        // La telemetría solo cubre 5 km, pero el resumen de la actividad (sesión/laps) dice 10 km.
        // Los splits deben sumar la distancia del resumen y no quedarse en 5.
        List<ActivityTrackpoint> points = buildSeries(500, 10.0, 4);

        AnalyticsService service = serviceWithActivity(10_000.0, points);

        List<SplitDto> splits = service.calculateSplits(USER_ID, ACTIVITY_ID);

        assertThat(splits).hasSize(10);
        assertThat(splits.stream().mapToDouble(SplitDto::getDistanceMeters).sum()).isEqualTo(10_000.0);
    }

    private AnalyticsService serviceWithActivity(double distanceMeters, List<ActivityTrackpoint> points) {
        Activity activity = Activity.builder()
                .id(ACTIVITY_ID)
                .distanceMeters(distanceMeters)
                .trackpoints(points)
                .build();

        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.of(activity));
        return new AnalyticsService(repository);
    }

    private List<ActivityTrackpoint> buildSeries(int intervals, double metersPerInterval, int secondsPerInterval) {
        LocalDateTime base = LocalDateTime.of(2026, 10, 4, 11, 12, 43);
        List<ActivityTrackpoint> points = new ArrayList<>(intervals + 1);
        for (int i = 0; i <= intervals; i++) {
            points.add(ActivityTrackpoint.builder()
                    .distanceMeters(i * metersPerInterval)
                    .timestamp(base.plusSeconds((long) i * secondsPerInterval))
                    .heartRate(150)
                    .build());
        }
        return points;
    }
}
