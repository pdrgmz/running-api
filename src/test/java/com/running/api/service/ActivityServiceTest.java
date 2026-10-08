package com.running.api.service;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActivityServiceTest {

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
    private AnalyticsService analyticsService;

    @Mock
    private StatsService statsService;

    @InjectMocks
    private ActivityService activityService;

    @Test
    void testDeleteAllActivities() {
        when(activityRepository.count()).thenReturn(5L);

        int count = activityService.deleteAllActivities();

        assertEquals(5, count);
        verify(trackpointRepository, times(1)).deleteAll();
        verify(activityRepository, times(1)).deleteAll();
        verify(personalRecordRepository, times(1)).deleteAll();
        verify(summaryStatsRepository, times(1)).deleteAll();
        verify(summaryStatsRepository, times(1)).save(any());
        verify(backupStorageService, times(1)).clearStorage();
    }

    @Test
    void testDeleteActivityByIdNotFound() {
        when(activityRepository.findById("non-existent")).thenReturn(Optional.empty());

        boolean deleted = activityService.deleteActivityById("non-existent");

        assertFalse(deleted);
    }

    @Test
    void testDeleteActivityByIdSuccess() {
        Activity act = Activity.builder().id("act-1").build();
        when(activityRepository.findById("act-1")).thenReturn(Optional.of(act));

        boolean deleted = activityService.deleteActivityById("act-1");

        assertTrue(deleted);
        verify(activityRepository, times(1)).delete(act);
        verify(statsService, times(1)).recalculateStatsAndRecords();
    }
}
