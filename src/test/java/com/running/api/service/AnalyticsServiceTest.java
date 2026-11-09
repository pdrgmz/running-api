package com.running.api.service;

import com.running.api.dto.HrZonesResponseDto;
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

    @Test
    void calculatesHrZonesAgainstAthleteMaxHeartRateInsteadOfActivityPeak() {
        // Todas las muestras a 150 ppm. El pico de la actividad es 170, pero la FC máxima
        // registrada del atleta es 200: 150/200 = 75 % -> Z3. Con el pico de la sesión
        // (150/170 = 88 %) el mismo rodaje habría aparecido como Z4.
        Activity activity = Activity.builder()
                .id(ACTIVITY_ID)
                .userId(USER_ID)
                .distanceMeters(5_000.0)
                .maxHeartRate(170)
                .trackpoints(buildConstantHeartRateSeries(120, 150))
                .build();

        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.of(activity));
        when(repository.findMaxHeartRatesByUserId(USER_ID)).thenReturn(List.of(200));

        HrZonesResponseDto zones = new AnalyticsService(repository).calculateHrZones(USER_ID, ACTIVITY_ID);

        assertThat(zones.getMaxHrUsed()).isEqualTo(200);
        assertThat(zones.getZones().getZ3Tempo().getPercentage()).isEqualTo(100.0);
        assertThat(zones.getZones().getZ4Threshold().getSeconds()).isZero();
    }

    @Test
    void ignoresImplausibleHeartRatesAndFallsBackToConfiguredMaxHr() {
        // Pico de 250 ppm (artefacto del sensor) y sin histórico: se usa el valor por defecto.
        Activity activity = Activity.builder()
                .id(ACTIVITY_ID)
                .userId(USER_ID)
                .distanceMeters(3_000.0)
                .maxHeartRate(250)
                .trackpoints(buildConstantHeartRateSeries(60, 100))
                .build();

        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.of(activity));
        when(repository.findMaxHeartRatesByUserId(USER_ID)).thenReturn(List.of());

        HrZonesResponseDto zones = new AnalyticsService(repository).calculateHrZones(USER_ID, ACTIVITY_ID);

        assertThat(zones.getMaxHrUsed()).isEqualTo(185);
        // 100 / 185 = 54 % -> Z1
        assertThat(zones.getZones().getZ1Recovery().getPercentage()).isEqualTo(100.0);
    }

    @Test
    void ignoresAnIsolatedMaxHeartRatePeakWhenResolvingAthleteReference() {
        // Historial realista: los picos de sesión se mueven entre 176 y 190, con un único
        // 200 aislado (sobrelectura de un sensor óptico). El cerco de Tukey descarta el 200
        // y la referencia del atleta queda en 190, no en el pico puntual.
        Activity activity = Activity.builder()
                .id(ACTIVITY_ID)
                .userId(USER_ID)
                .distanceMeters(5_000.0)
                .maxHeartRate(200)
                .trackpoints(buildConstantHeartRateSeries(120, 150))
                .build();

        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.of(activity));
        when(repository.findMaxHeartRatesByUserId(USER_ID))
                .thenReturn(List.of(176, 178, 179, 180, 181, 182, 183, 184, 185, 186, 190));

        HrZonesResponseDto zones = new AnalyticsService(repository).calculateHrZones(USER_ID, ACTIVITY_ID);

        assertThat(zones.getMaxHrUsed()).isEqualTo(190);
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

    private List<ActivityTrackpoint> buildConstantHeartRateSeries(int count, int heartRate) {
        LocalDateTime base = LocalDateTime.of(2026, 11, 3, 21, 34, 6);
        List<ActivityTrackpoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(ActivityTrackpoint.builder()
                    .distanceMeters(i * 2.0)
                    .timestamp(base.plusSeconds(i))
                    .heartRate(heartRate)
                    .build());
        }
        return points;
    }
}
