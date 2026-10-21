package com.running.api.service;

import com.running.api.model.ActivityTrackpoint;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.repository.PersonalRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 8, 0);

    private final StatsService service = new StatsService(null, null, null, null);

    private ActivityTrackpoint point(int second, Double distanceMeters) {
        return ActivityTrackpoint.builder()
                .timestamp(BASE.plusSeconds(second))
                .distanceMeters(distanceMeters)
                .build();
    }

    @Test
    void interpolatesExactTargetDistance() {
        // Velocidad constante 3 m/s: los 1000 m exactos se cubren en 333.33 s
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i <= 400; i++) {
            points.add(point(i, 3.0 * i));
        }

        Double fastest = service.findFastestSegment(points, 1000.0);

        assertThat(fastest).isCloseTo(1000.0 / 3.0, within(1e-6));

        // La búsqueda por muestras (implementación antigua) se pasa del target: 334 s.
        assertThat(fastest).isLessThanOrEqualTo(naiveSampleSearch(points, 1000.0));
    }

    @Test
    void findsFastestSegmentInTheMiddle() {
        // 500 s a 2 m/s (0..1000 m) + 200 s a 5 m/s (1000..2000 m)
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i <= 500; i++) {
            points.add(point(i, 2.0 * i));
        }
        for (int i = 1; i <= 200; i++) {
            points.add(point(500 + i, 1000.0 + 5.0 * i));
        }

        // El mejor 1k son los 1000..2000 m recorridos a 5 m/s -> 200 s
        assertThat(service.findFastestSegment(points, 1000.0)).isCloseTo(200.0, within(1e-6));
    }

    @Test
    void returnsNullWhenTargetNotReachable() {
        List<ActivityTrackpoint> points = List.of(point(0, 0.0), point(1, 200.0), point(2, 400.0));

        assertThat(service.findFastestSegment(points, 1000.0)).isNull();
    }

    @Test
    void ignoresPointsWithoutDistance() {
        // Trote realista a 3 m/s con una muestra sin distancia en t=1 s.
        List<ActivityTrackpoint> points = List.of(
                point(0, 0.0),
                point(1, null),
                point(2, 6.0),
                point(3, 9.0),
                point(4, 12.0)
        );

        // Puntos válidos: (0s,0) (2s,6) (3s,9) (4s,12). Para 10.5 m se interpola
        // entre 9 m (t=3) y 12 m (t=4): t = 3 + (10.5-9)/(12-9) = 3.5 s
        assertThat(service.findFastestSegment(points, 10.5)).isCloseTo(3.5, within(1e-9));
    }

    @Test
    void ignoresImpossibleDistanceJump() {
        // Trote real a 3 m/s (0..1200 m en 0..400 s) y, en el segundo 401, un salto corrupto
        // del sensor a 9000 m que después se congela.
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i <= 400; i++) {
            points.add(point(i, 3.0 * i));
        }
        points.add(point(401, 9000.0));
        for (int i = 402; i <= 420; i++) {
            points.add(point(i, 9000.0));
        }

        // Sin sanear, el salto permitiría "cubrir" 1000 m en ~0.22 s. Debe ignorarse y devolver
        // el mejor tramo real: los 1000 m a 3 m/s = 333.33 s.
        assertThat(service.findFastestSegment(points, 1000.0)).isCloseTo(1000.0 / 3.0, within(1e-6));
    }

    @Test
    void ignoresJumpWhenTargetUnreachableWithoutIt() {
        // 200 m reales en 100 s y luego un salto de 5000 m.
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            points.add(point(i, 2.0 * i));
        }
        points.add(point(101, 5200.0));
        for (int i = 102; i <= 120; i++) {
            points.add(point(i, 5200.0));
        }

        // El salto no debe permitir "cubrir" 1 km en 0.2 s: no hay 1 km de distancia real.
        assertThat(service.findFastestSegment(points, 1000.0)).isNull();
    }

    @Test
    void handlesDistanceCounterReset() {
        // La distancia acumulada se reinicia a 0 a mitad de serie: los incrementos posteriores
        // deben conservarse sobre una serie monótona.
        List<ActivityTrackpoint> points = List.of(
                point(0, 0.0),
                point(1, 3.0),
                point(2, 6.0),
                point(3, 9.0),
                point(4, 0.0),
                point(5, 3.0),
                point(6, 6.0)
        );

        // Serie reconstruida: 0,3,6,9,9,12,15 -> los 12 m se alcanzan en t=5 s.
        assertThat(service.findFastestSegment(points, 12.0)).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void returnsNullWithInsufficientPoints() {
        assertThat(service.findFastestSegment(List.of(), 1000.0)).isNull();
        assertThat(service.findFastestSegment(List.of(point(0, 0.0)), 1000.0)).isNull();
        assertThat(service.findFastestSegment(List.of(point(0, null), point(1, null)), 1000.0)).isNull();
    }

    @Test
    void recalculateStatsAndRecordsReusesTheExistingSummaryRow() {
        // Reinsertar el resumen con otra fila choca con la restricción única de user_id:
        // Hibernate ejecuta los INSERT antes que los DELETE diferidos de la transacción.
        ActivityRepository activityRepository = mock(ActivityRepository.class);
        ActivityTrackpointRepository trackpointRepository = mock(ActivityTrackpointRepository.class);
        GlobalSummaryStatsRepository summaryStatsRepository = mock(GlobalSummaryStatsRepository.class);
        PersonalRecordRepository personalRecordRepository = mock(PersonalRecordRepository.class);
        StatsService mockService = new StatsService(
                activityRepository, trackpointRepository, summaryStatsRepository, personalRecordRepository);

        when(activityRepository.calculateAggregatedSummary(5L)).thenReturn(
                GlobalSummaryStats.builder().totalActivities(3L).totalDistanceMeters(12000.0).build());
        when(summaryStatsRepository.findByUserId(5L)).thenReturn(Optional.of(
                GlobalSummaryStats.builder().id(42L).userId(5L).build()));
        when(activityRepository.findByUserIdAndDistanceMetersGreaterThanEqual(eq(5L), anyDouble()))
                .thenReturn(List.of());

        mockService.recalculateStatsAndRecords(5L);

        verify(summaryStatsRepository, never()).deleteByUserId(anyLong());
        ArgumentCaptor<GlobalSummaryStats> stats = ArgumentCaptor.forClass(GlobalSummaryStats.class);
        verify(summaryStatsRepository).save(stats.capture());
        assertThat(stats.getValue().getId()).isEqualTo(42L);
        assertThat(stats.getValue().getUserId()).isEqualTo(5L);
        assertThat(stats.getValue().getTotalActivities()).isEqualTo(3L);
        assertThat(stats.getValue().getTotalDistanceMeters()).isEqualTo(12000.0);
    }

    /**
     * Réplica de la implementación original (muestreo: primer punto que cubre el target),
     * para comprobar que la nueva versión nunca es peor.
     */
    private Double naiveSampleSearch(List<ActivityTrackpoint> points, double targetMeters) {
        Double minTimeSec = null;
        for (int left = 0; left < points.size(); left++) {
            if (points.get(left).getDistanceMeters() == null) continue;
            for (int right = left + 1; right < points.size(); right++) {
                if (points.get(right).getDistanceMeters() == null) continue;
                double diff = points.get(right).getDistanceMeters() - points.get(left).getDistanceMeters();
                if (diff >= targetMeters) {
                    long duration = Duration.between(
                            points.get(left).getTimestamp(), points.get(right).getTimestamp()).getSeconds();
                    if (duration > 0 && (minTimeSec == null || duration < minTimeSec)) {
                        minTimeSec = (double) duration;
                    }
                    break;
                }
            }
        }
        return minTimeSec;
    }
}
