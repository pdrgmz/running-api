package com.running.api.service;

import com.running.api.dto.MetricsRecalculationResponseDto;
import com.running.api.dto.TrainingLoadResponseDto;
import com.running.api.dto.Vo2MaxVamResponseDto;
import com.running.api.model.Activity;
import com.running.api.repository.ActivityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsRecalculationServiceTest {

    private static final Long USER_ID = 1L;

    @Test
    void recalculatesTrainingLoadAndVo2MaxForEveryActivityUsingAthleteMaxHr() {
        Activity first = Activity.builder().id("a1").userId(USER_ID).build();
        Activity second = Activity.builder().id("a2").userId(USER_ID).build();

        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        AnalyticsService analytics = mock(AnalyticsService.class);
        when(analytics.resolveAthleteMaxHr(eq(USER_ID), isNull())).thenReturn(200);
        when(analytics.computeTrainingLoadDetails(any(Activity.class), eq(200)))
                .thenReturn(TrainingLoadResponseDto.builder().trainingLoad(42.5).build());
        when(analytics.computeVo2MaxAndVamDetails(any(Activity.class), eq(200)))
                .thenReturn(Vo2MaxVamResponseDto.builder().vo2MaxEstimated(51.2).vamKmH(14.63).build());

        EntityManager entityManager = mock(EntityManager.class);

        MetricsRecalculationService service = new MetricsRecalculationService(repository, analytics);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        MetricsRecalculationResponseDto result = service.recalculateAll(USER_ID);

        assertThat(result.getActivitiesProcessed()).isEqualTo(2);
        assertThat(result.getTrainingLoadRecalculated()).isEqualTo(2);
        assertThat(result.getVo2MaxRecalculated()).isEqualTo(2);
        assertThat(result.getAthleteMaxHrUsed()).isEqualTo(200);
        assertThat(first.getTrainingLoad()).isEqualTo(42.5);
        assertThat(second.getTrainingLoad()).isEqualTo(42.5);
        assertThat(first.getVo2MaxEstimated()).isEqualTo(51.2);
        assertThat(second.getVamKmH()).isEqualTo(14.63);

        // El contexto de persistencia se vacía al terminar cada lote.
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void returnsZeroCountsWhenTheUserHasNoActivities() {
        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        AnalyticsService analytics = mock(AnalyticsService.class);
        when(analytics.resolveAthleteMaxHr(eq(USER_ID), isNull())).thenReturn(185);

        MetricsRecalculationService service = new MetricsRecalculationService(repository, analytics);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        MetricsRecalculationResponseDto result = service.recalculateAll(USER_ID);

        assertThat(result.getActivitiesProcessed()).isZero();
        assertThat(result.getTrainingLoadRecalculated()).isZero();
        assertThat(result.getVo2MaxRecalculated()).isZero();
        assertThat(result.getAthleteMaxHrUsed()).isEqualTo(185);
    }
}
