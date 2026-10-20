package com.running.api.service;

import com.running.api.repository.ActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ElevationBackfillServiceTest {

    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final ElevationBackfillWorker worker = mock(ElevationBackfillWorker.class);
    private final FileService fileService = mock(FileService.class);

    @Test
    void processesActivitiesUntilTheBudgetIsExhaustedAndRecalculatesStats() {
        when(activityRepository.findActivityIdsWithMissingAltitude(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of("a1", "a2"));
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
}
