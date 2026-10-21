package com.running.api.service;

import com.running.api.model.Activity;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.repository.PersonalRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private FileService fileService;

    @Mock
    private StatsService statsService;

    @Mock
    private TrackpointCompletionService trackpointCompletionService;

    @Mock
    private ActivityDeletionStatusTracker deletionStatusTracker;

    @InjectMocks
    private ActivityService activityService;

    @Test
    void deletesAllActivitiesAndResetsStatsReportingProgress() {
        Activity first = Activity.builder().id("a1").userId(7L).build();
        Activity second = Activity.builder().id("a2").userId(7L).build();
        when(activityRepository.findByUserId(7L)).thenReturn(List.of(first, second));

        activityService.deleteAllActivities(7L, "delete-1");

        InOrder order = inOrder(deletionStatusTracker);
        order.verify(deletionStatusTracker).markProcessing("delete-1", 2);
        order.verify(deletionStatusTracker).updateProgress("delete-1", 2);
        order.verify(deletionStatusTracker).markCompleted("delete-1", 2);

        verify(activityRepository).deleteAll(List.of(first, second));
        verify(personalRecordRepository).deleteByUserId(7L);
        verify(summaryStatsRepository).deleteByUserId(7L);

        ArgumentCaptor<GlobalSummaryStats> stats = ArgumentCaptor.forClass(GlobalSummaryStats.class);
        verify(summaryStatsRepository).save(stats.capture());
        assertThat(stats.getValue().getUserId()).isEqualTo(7L);
        assertThat(stats.getValue().getTotalActivities()).isZero();
        assertThat(stats.getValue().getTotalDistanceMeters()).isZero();
        assertThat(stats.getValue().getTotalElevationGain()).isZero();
    }

    @Test
    void deletesLotsInBatchesAndReportsPartialProgress() {
        List<Activity> activities = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            activities.add(Activity.builder().id("a" + i).userId(7L).build());
        }
        when(activityRepository.findByUserId(7L)).thenReturn(activities);

        activityService.deleteAllActivities(7L, "delete-1");

        verify(activityRepository).deleteAll(activities.subList(0, 10));
        verify(activityRepository).deleteAll(activities.subList(10, 12));
        verify(deletionStatusTracker).updateProgress("delete-1", 10);
        verify(deletionStatusTracker).updateProgress("delete-1", 12);
        verify(deletionStatusTracker).markCompleted("delete-1", 12);
    }

    @Test
    void marksTheDeletionAsFailedAndPropagatesTheError() {
        when(activityRepository.findByUserId(7L)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> activityService.deleteAllActivities(7L, "delete-1"))
                .isInstanceOf(IllegalStateException.class);

        verify(deletionStatusTracker).markFailed("delete-1", "boom");
        verify(deletionStatusTracker, never()).markCompleted(anyString(), anyLong());
        verify(summaryStatsRepository, never()).save(any(GlobalSummaryStats.class));
    }
}
