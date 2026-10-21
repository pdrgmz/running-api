package com.running.api.service;

import com.running.api.dto.ElevationCoverageDto;
import com.running.api.dto.TrackpointMetricsBatchCompletionResultDto;
import com.running.api.dto.TrackpointMetricsCompletionResultDto;
import com.running.api.model.Activity;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.repository.PersonalRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

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

    @InjectMocks
    private ActivityService activityService;

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

        TrackpointMetricsBatchCompletionResultDto result =
                activityService.completeAllActivityMetrics(1L);

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

        TrackpointMetricsBatchCompletionResultDto result =
                activityService.completeAllActivityMetrics(1L);

        assertThat(result.getActivitiesFound()).isEqualTo(2);
        assertThat(result.getActivitiesProcessed()).isEqualTo(1);
        assertThat(result.getActivitiesUpdated()).isEqualTo(1);
        assertThat(result.getActivitiesFailed()).isEqualTo(1);
        assertThat(result.getErrors()).containsEntry("bad", "datos corruptos");
        assertThat(result.getMessage()).contains("fallidas: 1");

        verify(statsService).recalculateStatsAndRecords(1L);
    }

    @Test
    void returnsEmptySummaryWhenThereAreNoActivities() {
        when(activityRepository.findIdsByUserId(1L)).thenReturn(List.of());

        TrackpointMetricsBatchCompletionResultDto result =
                activityService.completeAllActivityMetrics(1L);

        assertThat(result.getActivitiesFound()).isZero();
        assertThat(result.getActivitiesProcessed()).isZero();
        assertThat(result.getMessage()).isEqualTo("No hay actividades para procesar");
        verifyNoInteractions(statsService);
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
        ElevationCoverageDto coverage = ActivityService.buildElevationCoverage(200, 150);

        assertThat(coverage.getTotalTrackpoints()).isEqualTo(200);
        assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(150);
        assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(50);
        assertThat(coverage.getWithElevationPercent()).isEqualTo(75.0);
        assertThat(coverage.getMissingElevationPercent()).isEqualTo(25.0);
    }

    @Test
    void roundsPercentagesToTwoDecimals() {
        ElevationCoverageDto coverage = ActivityService.buildElevationCoverage(3, 1);

        assertThat(coverage.getWithElevationPercent()).isEqualTo(33.33);
        assertThat(coverage.getMissingElevationPercent()).isEqualTo(66.67);
    }

    @Test
    void returnsZeroPercentagesWhenThereAreNoTrackpoints() {
        ElevationCoverageDto coverage = ActivityService.buildElevationCoverage(0, 0);

        assertThat(coverage.getTotalTrackpoints()).isZero();
        assertThat(coverage.getTrackpointsWithElevation()).isZero();
        assertThat(coverage.getTrackpointsMissingElevation()).isZero();
        assertThat(coverage.getWithElevationPercent()).isZero();
        assertThat(coverage.getMissingElevationPercent()).isZero();
    }

    @Test
    void clampsInconsistentCounts() {
        ElevationCoverageDto coverage = ActivityService.buildElevationCoverage(10, 12);

        assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(10);
        assertThat(coverage.getTrackpointsMissingElevation()).isZero();
        assertThat(coverage.getWithElevationPercent()).isEqualTo(100.0);
        assertThat(coverage.getMissingElevationPercent()).isZero();
    }
}
