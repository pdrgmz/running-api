package com.running.api.service;

import com.running.api.dto.AcwrStatusDto;
import com.running.api.dto.HrZonesResponseDto;
import com.running.api.dto.SplitDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.projection.DailyTrimpProjection;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void acwrIsUndeterminedWithoutActivities() {
        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findEarliestActivityDate(USER_ID)).thenReturn(Optional.empty());

        AcwrStatusDto acwr = new AnalyticsService(repository).calculateCurrentAcwr(USER_ID);

        assertThat(acwr.zone()).isEqualTo("UNDETERMINED");
        assertThat(acwr.acwr()).isZero();
    }

    @Test
    void acwrIsUndeterminedWithLessThanAChronicWindow() {
        LocalDate today = LocalDate.now();
        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findEarliestActivityDate(USER_ID)).thenReturn(Optional.of(today.minusDays(10)));
        DailyTrimpProjection recentRun = trimp(today.minusDays(5), 50.0);
        when(repository.findDailyTrimpSum(eq(USER_ID), any(), any()))
                .thenReturn(List.of(recentRun));

        AcwrStatusDto acwr = new AnalyticsService(repository).calculateCurrentAcwr(USER_ID);

        assertThat(acwr.zone()).isEqualTo("UNDETERMINED");
        assertThat(acwr.acwr()).isZero();
    }

    @Test
    void acwrUsesA28DayChronicWindow() {
        // Carga alta antigua (200) durante 73 días y luego 28 días suaves (20). La ventana
        // de 28 días pesa bastante menos que la de 42: el test fija el resultado del ACWR
        // estándar 7:28, no el del CTL de 42 días que se usa en la curva PMC.
        LocalDate today = LocalDate.now();
        LocalDate earliest = today.minusDays(100);
        List<DailyTrimpProjection> daily = new ArrayList<>();
        for (int daysAgo = 100; daysAgo >= 28; daysAgo--) {
            daily.add(trimp(today.minusDays(daysAgo), 200.0));
        }
        for (int daysAgo = 27; daysAgo >= 0; daysAgo--) {
            daily.add(trimp(today.minusDays(daysAgo), 20.0));
        }

        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findEarliestActivityDate(USER_ID)).thenReturn(Optional.of(earliest));
        when(repository.findDailyTrimpSum(eq(USER_ID), any(), any())).thenReturn(daily);

        AcwrStatusDto acwr = new AnalyticsService(repository).calculateCurrentAcwr(USER_ID);

        // EWMA 7/28 -> ATL 23.30, CTL 80.79, ratio 0.29. Con 7/42 el CTL sería ~94.36.
        assertThat(acwr.acuteLoad()).isCloseTo(23.30, within(0.05));
        assertThat(acwr.chronicLoad()).isCloseTo(80.79, within(0.05));
        assertThat(acwr.acwr()).isCloseTo(0.29, within(0.01));
        assertThat(acwr.zone()).isEqualTo("UNDERTRAINING");
    }

    @Test
    void refreshHeartRateMetricsUsesAthleteReferenceInsteadOfSessionPeak() {
        // El pico de la sesión es 200, pero la referencia robusta del atleta es 184. Con
        // 150 bpm constantes, Edwards cae en Z3 (x3) contra 184 y en Z2 (x2) contra 200.
        Activity activity = Activity.builder()
                .id(ACTIVITY_ID)
                .userId(USER_ID)
                .totalTimeSeconds(600.0)
                .avgHeartRate(150)
                .maxHeartRate(200)
                .trackpoints(buildConstantHeartRateSeries(60, 150))
                .build();

        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findMaxHeartRatesByUserId(USER_ID))
                .thenReturn(List.of(170, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184));

        new AnalyticsService(repository).refreshHeartRateMetrics(activity);

        assertThat(activity.getTrainingLoad()).isCloseTo(3.0, within(0.05));
    }

    private DailyTrimpProjection trimp(LocalDate date, double totalTrimp) {
        DailyTrimpProjection projection = mock(DailyTrimpProjection.class);
        when(projection.getDate()).thenReturn(date);
        when(projection.getTotalTrimp()).thenReturn(totalTrimp);
        return projection;
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
