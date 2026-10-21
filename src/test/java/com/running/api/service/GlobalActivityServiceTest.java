package com.running.api.service;

import com.running.api.dto.ElevationCoverageDto;
import com.running.api.dto.TrackpointMetricsBatchCompletionResultDto;
import com.running.api.dto.TrackpointMetricsCompletionResultDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.repository.PersonalRecordRepository;
import com.running.api.repository.projection.ElevationCoverageProjection;
import com.running.api.repository.projection.FlatElevationProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalActivityServiceTest {

        @Mock
        private ActivityRepository activityRepository;
        @Mock
        private ActivityTrackpointRepository trackpointRepository;
        @Mock
        private GlobalSummaryStatsRepository summaryStatsRepository;
        @Mock
        private PersonalRecordRepository personalRecordRepository;
        @Mock
        private BackupStorageService backupStorageService;
        @Mock
        private FileService fileService;
        @Mock
        private StatsService statsService;
        @Mock
        private TrackpointCompletionService trackpointCompletionService;
        @Mock
        private PlatformTransactionManager transactionManager;
        @Mock
        private ElevationProvider elevationProvider;

        @InjectMocks
        private GlobalActivityService globalActivityService;

        @Test
        void completesMetricsForAllActivitiesAndAggregatesResults() {
                when(activityRepository.findIdsByUserId(1L)).thenReturn(List.of("a", "b", "empty"));
                when(transactionManager.getTransaction(any()))
                                .thenAnswer(invocation -> new SimpleTransactionStatus());

                Activity a = Activity.builder().id("a").userId(1L).build();
                Activity b = Activity.builder().id("b").userId(1L).build();
                Activity empty = Activity.builder().id("empty").userId(1L).build();

                when(activityRepository.findByIdAndUserId("a", 1L)).thenReturn(Optional.of(a));
                when(activityRepository.findByIdAndUserId("b", 1L)).thenReturn(Optional.of(b));
                when(activityRepository.findByIdAndUserId("empty", 1L)).thenReturn(Optional.of(empty));

                when(trackpointCompletionService.completeMetrics(a))
                                .thenReturn(metricsResult("a", 10, 1, 2, 3, 1, 1, 1, 2, 100.5));
                when(trackpointCompletionService.completeMetrics(b))
                                .thenReturn(metricsResult("b", 20, 4, 5, 6, 2, 2, 2, 3, 50.5));
                when(trackpointCompletionService.completeMetrics(empty))
                                .thenReturn(metricsResult("empty", 0, 0, 0, 0, 0, 0, 0, 0, 0.0));

                TrackpointMetricsBatchCompletionResultDto result = globalActivityService.completeAllActivityMetrics(1L);

                assertThat(result.getActivitiesFound()).isEqualTo(3);
                assertThat(result.getActivitiesProcessed()).isEqualTo(3);
                assertThat(result.getActivitiesUpdated()).isEqualTo(2);
                assertThat(result.getActivitiesWithoutTrackpoints()).isEqualTo(1);
                assertThat(result.getActivitiesFailed()).isZero();
                assertThat(result.getTotalTrackpoints()).isEqualTo(30);
                assertThat(result.getHeartRateFilled()).isEqualTo(5);
                assertThat(result.getCadenceFilled()).isEqualTo(7);
                assertThat(result.getSpeedFilled()).isEqualTo(9);
                assertThat(result.getHeartRateRemoved()).isEqualTo(3);
                assertThat(result.getCadenceRemoved()).isEqualTo(3);
                assertThat(result.getSpeedRemoved()).isEqualTo(3);
                assertThat(result.getTotalFilled()).isEqualTo(21);
                assertThat(result.getTotalRemoved()).isEqualTo(9);
                assertThat(result.getDistanceAdjusted()).isEqualTo(5);
                assertThat(result.getDistanceDiscardedMeters()).isEqualTo(151.0);
                assertThat(result.getErrors()).isEmpty();
                assertThat(result.getMessage()).contains("actualizadas: 2", "sin trackpoints: 1");

                verify(statsService).recalculateStatsAndRecords(1L);
        }

        @Test
        void continuesWithOtherActivitiesWhenOneFails() {
                when(activityRepository.findIdsByUserId(1L)).thenReturn(List.of("ok", "bad"));
                when(transactionManager.getTransaction(any()))
                                .thenAnswer(invocation -> new SimpleTransactionStatus());

                Activity ok = Activity.builder().id("ok").userId(1L).build();
                Activity bad = Activity.builder().id("bad").userId(1L).build();

                when(activityRepository.findByIdAndUserId("ok", 1L)).thenReturn(Optional.of(ok));
                when(activityRepository.findByIdAndUserId("bad", 1L)).thenReturn(Optional.of(bad));
                when(trackpointCompletionService.completeMetrics(ok))
                                .thenReturn(metricsResult("ok", 5, 1, 1, 1, 0, 0, 0, 0, 0.0));
                when(trackpointCompletionService.completeMetrics(bad))
                                .thenThrow(new IllegalStateException("datos corruptos"));

                TrackpointMetricsBatchCompletionResultDto result = globalActivityService.completeAllActivityMetrics(1L);

                assertThat(result.getActivitiesFound()).isEqualTo(2);
                assertThat(result.getActivitiesProcessed()).isEqualTo(1);
                assertThat(result.getActivitiesUpdated()).isEqualTo(1);
                assertThat(result.getActivitiesFailed()).isEqualTo(1);
                assertThat(result.getErrors()).containsEntry("bad", "datos corruptos");
                assertThat(result.getMessage()).contains("fallidas: 1");

                verify(statsService).recalculateStatsAndRecords(1L);
        }

        @Test
        void reportsDistanceAdjustmentsEvenWhenNoValuesWereRemoved() {
                when(activityRepository.findIdsByUserId(1L)).thenReturn(List.of("a"));
                when(transactionManager.getTransaction(any()))
                                .thenAnswer(invocation -> new SimpleTransactionStatus());

                Activity a = Activity.builder().id("a").userId(1L).build();
                when(activityRepository.findByIdAndUserId("a", 1L)).thenReturn(Optional.of(a));
                when(trackpointCompletionService.completeMetrics(a))
                                .thenReturn(metricsResult("a", 5, 0, 0, 0, 0, 0, 0, 2, 10.0));

                TrackpointMetricsBatchCompletionResultDto result = globalActivityService.completeAllActivityMetrics(1L);

                assertThat(result.getMessage()).contains("se ajustaron 2 distancias");
        }

        @Test
        void returnsEmptySummaryWhenThereAreNoActivities() {
                when(activityRepository.findIdsByUserId(1L)).thenReturn(List.of());

                TrackpointMetricsBatchCompletionResultDto result = globalActivityService.completeAllActivityMetrics(1L);

                assertThat(result.getActivitiesFound()).isZero();
                assertThat(result.getActivitiesProcessed()).isZero();
                assertThat(result.getMessage()).isEqualTo("No hay actividades para procesar");
                verifyNoInteractions(statsService);
        }

        @Test
        void globalCompletionOnlyFillsMetricsAndKeepsCoordinatesAndAltitudeUntouched() {
                when(activityRepository.findIdsByUserId(1L)).thenReturn(List.of("activity"));
                when(transactionManager.getTransaction(any()))
                                .thenAnswer(invocation -> new SimpleTransactionStatus());

                Activity activity = Activity.builder()
                                .id("activity")
                                .userId(1L)
                                .trackpoints(new ArrayList<>())
                                .build();
                activity.addTrackpoint(ActivityTrackpoint.builder()
                                .latitude(40.000)
                                .longitude(-3.000)
                                .altitudeMeters(100.0)
                                .heartRate(140)
                                .cadence(80)
                                .speed(3.0)
                                .build());
                activity.addTrackpoint(ActivityTrackpoint.builder()
                                .latitude(null)
                                .longitude(null)
                                .altitudeMeters(null)
                                .heartRate(null)
                                .cadence(null)
                                .speed(null)
                                .build());
                activity.addTrackpoint(ActivityTrackpoint.builder()
                                .latitude(40.002)
                                .longitude(-3.002)
                                .altitudeMeters(200.0)
                                .heartRate(160)
                                .cadence(90)
                                .speed(5.0)
                                .build());

                when(activityRepository.findByIdAndUserId("activity", 1L)).thenReturn(Optional.of(activity));

                TrackpointCompletionService realCompletionService =
                                new TrackpointCompletionService(elevationProvider, 10, 0.1, false);
                GlobalActivityService service = new GlobalActivityService(activityRepository, trackpointRepository,
                                statsService, realCompletionService, transactionManager);

                TrackpointMetricsBatchCompletionResultDto result = service.completeAllActivityMetrics(1L);

                ActivityTrackpoint middle = activity.getTrackpoints().get(1);
                assertThat(middle.getHeartRate()).isEqualTo(150);
                assertThat(middle.getCadence()).isEqualTo(85);
                assertThat(middle.getSpeed()).isCloseTo(4.0, within(1e-9));

                assertThat(middle.getLatitude()).isNull();
                assertThat(middle.getLongitude()).isNull();
                assertThat(middle.getAltitudeMeters()).isNull();
                assertThat(activity.getTrackpoints().get(0).getLatitude()).isEqualTo(40.000);
                assertThat(activity.getTrackpoints().get(0).getAltitudeMeters()).isEqualTo(100.0);
                assertThat(activity.getTrackpoints().get(2).getLatitude()).isEqualTo(40.002);
                assertThat(activity.getTrackpoints().get(2).getAltitudeMeters()).isEqualTo(200.0);

                assertThat(result.getActivitiesProcessed()).isEqualTo(1);
                assertThat(result.getTotalFilled()).isEqualTo(3);
                assertThat(result.getTotalRemoved()).isZero();
                verify(statsService).recalculateStatsAndRecords(1L);
        }

        private static TrackpointMetricsCompletionResultDto metricsResult(String activityId,
                        int totalTrackpoints,
                        int heartRateFilled,
                        int cadenceFilled,
                        int speedFilled,
                        int heartRateRemoved,
                        int cadenceRemoved,
                        int speedRemoved,
                        int distanceAdjusted,
                        double distanceDiscardedMeters) {
                return TrackpointMetricsCompletionResultDto.builder()
                                .activityId(activityId)
                                .totalTrackpoints(totalTrackpoints)
                                .heartRateFilled(heartRateFilled)
                                .cadenceFilled(cadenceFilled)
                                .speedFilled(speedFilled)
                                .heartRateRemoved(heartRateRemoved)
                                .cadenceRemoved(cadenceRemoved)
                                .speedRemoved(speedRemoved)
                                .totalFilled(heartRateFilled + cadenceFilled + speedFilled)
                                .totalRemoved(heartRateRemoved + cadenceRemoved + speedRemoved)
                                .distanceAdjusted(distanceAdjusted)
                                .distanceDiscardedMeters(distanceDiscardedMeters)
                                .message("ok")
                                .build();
        }

        @Test
        void calculatesElevationCoveragePercentages() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(200, 150, 0);

                assertThat(coverage.getTotalTrackpoints()).isEqualTo(200);
                assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(150);
                assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(50);
                assertThat(coverage.getTrackpointsWithFlatElevation()).isZero();
                assertThat(coverage.getWithElevationPercent()).isEqualTo(75.0);
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(25.0);
                assertThat(coverage.getFlatElevationPercent()).isZero();
        }

        @Test
        void roundsPercentagesToTwoDecimals() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(3, 1, 0);

                assertThat(coverage.getWithElevationPercent()).isEqualTo(33.33);
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(66.67);
        }

        @Test
        void returnsZeroPercentagesWhenThereAreNoTrackpoints() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(0, 0, 0);

                assertThat(coverage.getTotalTrackpoints()).isZero();
                assertThat(coverage.getTrackpointsWithElevation()).isZero();
                assertThat(coverage.getTrackpointsMissingElevation()).isZero();
                assertThat(coverage.getTrackpointsWithFlatElevation()).isZero();
                assertThat(coverage.getWithElevationPercent()).isZero();
                assertThat(coverage.getMissingElevationPercent()).isZero();
                assertThat(coverage.getFlatElevationPercent()).isZero();
        }

        @Test
        void clampsInconsistentCounts() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(10, 12, 0);

                assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(10);
                assertThat(coverage.getTrackpointsMissingElevation()).isZero();
                assertThat(coverage.getWithElevationPercent()).isEqualTo(100.0);
                assertThat(coverage.getMissingElevationPercent()).isZero();
        }

        @Test
        void subtractsFlatAltitudesFromTheValidCountAndCountsThemAsPending() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(200, 150, 40);

                assertThat(coverage.getTotalTrackpoints()).isEqualTo(200);
                assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(110);
                assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(90);
                assertThat(coverage.getTrackpointsWithFlatElevation()).isEqualTo(40);
                assertThat(coverage.getWithElevationPercent()).isEqualTo(55.0);
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(45.0);
                assertThat(coverage.getFlatElevationPercent()).isEqualTo(20.0);
        }

        @Test
        void clampsFlatAltitudesToTheValidCount() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(10, 5, 8);

                assertThat(coverage.getTrackpointsWithElevation()).isZero();
                assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(10);
                assertThat(coverage.getTrackpointsWithFlatElevation()).isEqualTo(5);
                assertThat(coverage.getWithElevationPercent()).isZero();
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(100.0);
                assertThat(coverage.getFlatElevationPercent()).isEqualTo(50.0);
        }

        @Test
        void coverageTreatsFlatAltitudesAsPendingAndReportsThemSeparately() {
                when(trackpointRepository.getElevationCoverage(eq(1L), anyDouble()))
                                .thenReturn(coverageProjection(200L, 150L));
                when(trackpointRepository.findFlatElevationPoints(eq(1L), anyDouble(), anyInt(), anyInt()))
                                .thenReturn(List.of(flatGroup("a", 30L), flatGroup("b", 10L)));

                ElevationCoverageDto coverage = globalActivityService.getElevationCoverage(1L);

                assertThat(coverage.getTotalTrackpoints()).isEqualTo(200);
                assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(110);
                assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(90);
                assertThat(coverage.getTrackpointsWithFlatElevation()).isEqualTo(40);
                assertThat(coverage.getWithElevationPercent()).isEqualTo(55.0);
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(45.0);
                assertThat(coverage.getFlatElevationPercent()).isEqualTo(20.0);
        }

        private static ElevationCoverageProjection coverageProjection(Long total, Long withElevation) {
                return new ElevationCoverageProjection() {
                        @Override
                        public Long getTotalTrackpoints() {
                                return total;
                        }

                        @Override
                        public Long getTrackpointsWithElevation() {
                                return withElevation;
                        }
                };
        }

        private static FlatElevationProjection flatGroup(String activityId, Long occurrences) {
                return new FlatElevationProjection() {
                        @Override
                        public String getActivityId() {
                                return activityId;
                        }

                        @Override
                        public Long getOccurrences() {
                                return occurrences;
                        }
                };
        }
}
