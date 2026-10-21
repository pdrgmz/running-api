package com.running.api.service;

import com.running.api.repository.ActivityRepository;
import com.running.api.repository.projection.ElevationBackfillProjection;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ElevationBackfillServiceTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 8, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 1, 2, 8, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 1, 3, 8, 0);
    private static final LocalDateTime T4 = LocalDateTime.of(2026, 1, 4, 8, 0);

    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final ElevationBackfillWorker worker = mock(ElevationBackfillWorker.class);
    private final FileService fileService = mock(FileService.class);

    @Test
    void processesActivitiesUntilTheBudgetIsExhaustedAndRecalculatesStats() {
        when(activityRepository.findActivitiesWithMissingAltitude(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(candidate("a1", T1), candidate("a2", T2)));
        when(activityRepository.findActivitiesWithFlatAltitude(anyDouble(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(worker.processActivity(eq("a1"), anyInt(), anyDouble(), any()))
                .thenAnswer(invocation -> {
                    Set<Long> users = invocation.getArgument(3);
                    users.add(7L);
                    return 2;
                });
        when(worker.processActivity(eq("a2"), anyInt(), anyDouble(), any()))
                .thenReturn(0);

        ElevationBackfillService service =
                new ElevationBackfillService(activityRepository, worker, fileService,
                        true, true, 80, 50, 90.0);

        service.backfillMissingAltitudes();

        verify(worker).processActivity(eq("a1"), eq(80), eq(90.0), any());
        verify(worker).processActivity(eq("a2"), eq(78), eq(90.0), any());
        verify(fileService).recalculateAllStats(7L);
    }

    @Test
    void mergesMissingAndFlatCandidatesInStartTimeOrderWithoutDuplicates() {
        when(activityRepository.findActivitiesWithMissingAltitude(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(candidate("missing-late", T4)));
        when(activityRepository.findActivitiesWithFlatAltitude(anyDouble(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(candidate("flat-first", T1),
                        candidate("missing-late", T4),
                        candidate("flat-third", T3)));
        when(worker.processActivity(anyString(), anyInt(), anyDouble(), any())).thenReturn(1);

        ElevationBackfillService service =
                new ElevationBackfillService(activityRepository, worker, fileService,
                        true, true, 80, 50, 90.0);

        service.backfillMissingAltitudes();

        InOrder order = inOrder(worker);
        order.verify(worker).processActivity(eq("flat-first"), eq(80), eq(90.0), any());
        order.verify(worker).processActivity(eq("flat-third"), eq(79), eq(90.0), any());
        order.verify(worker).processActivity(eq("missing-late"), eq(78), eq(90.0), any());
        verify(worker, times(3)).processActivity(anyString(), anyInt(), anyDouble(), any());
    }

    @Test
    void limitsMergedCandidatesToTheConfiguredMaximum() {
        when(activityRepository.findActivitiesWithMissingAltitude(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(candidate("a1", T1), candidate("a2", T2)));
        when(activityRepository.findActivitiesWithFlatAltitude(anyDouble(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(candidate("a3", T3)));
        when(worker.processActivity(anyString(), anyInt(), anyDouble(), any())).thenReturn(1);

        ElevationBackfillService service =
                new ElevationBackfillService(activityRepository, worker, fileService,
                        true, true, 80, 2, 90.0);

        service.backfillMissingAltitudes();

        verify(worker).processActivity(eq("a1"), eq(80), eq(90.0), any());
        verify(worker).processActivity(eq("a2"), eq(79), eq(90.0), any());
        verify(worker, never()).processActivity(eq("a3"), anyInt(), anyDouble(), any());
    }

    @Test
    void doesNothingWhenBackfillIsDisabled() {
        ElevationBackfillService service =
                new ElevationBackfillService(activityRepository, worker, fileService,
                        false, true, 80, 50, 90.0);

        service.backfillMissingAltitudes();

        verifyNoInteractions(activityRepository, worker, fileService);
    }

    @Test
    void doesNothingWhenExternalElevationIsDisabled() {
        ElevationBackfillService service =
                new ElevationBackfillService(activityRepository, worker, fileService,
                        true, false, 80, 50, 90.0);

        service.backfillMissingAltitudes();

        verifyNoInteractions(activityRepository, worker, fileService);
    }

    private static ElevationBackfillProjection candidate(String activityId, LocalDateTime startTime) {
        return new ElevationBackfillProjection() {
            @Override
            public String getActivityId() {
                return activityId;
            }

            @Override
            public LocalDateTime getStartTime() {
                return startTime;
            }
        };
    }
}
