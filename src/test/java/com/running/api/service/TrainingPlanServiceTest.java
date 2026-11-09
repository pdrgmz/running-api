package com.running.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.dto.PlanGenerationJobResponseDto;
import com.running.api.dto.TrainingPlanResponseDto;
import com.running.api.dto.WeeklyPlanPayload;
import com.running.api.dto.WeeklyPlanPayload.PlannedWorkoutPayload;
import com.running.api.exception.PlanAlreadyExistsException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.PlanGenerationJob;
import com.running.api.model.PlanStatus;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.TrainingJobStatus;
import com.running.api.model.TrainingPlan;
import com.running.api.model.WorkoutType;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.PlanGenerationJobRepository;
import com.running.api.repository.TrainingPlanRepository;
import com.running.api.repository.TrainingPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {

    private static final Long USER_ID = 7L;
    private static final String REQUEST_ID = "request-123";
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    @Mock
    private TrainingPlanRepository planRepository;
    @Mock
    private PlanGenerationJobRepository jobRepository;
    @Mock
    private TrainingPreferencesRepository preferencesRepository;
    @Mock
    private ActivityRepository activityRepository;

    private TrainingPlanService service;

    @BeforeEach
    void setUp() {
        service = new TrainingPlanService(
                planRepository,
                jobRepository,
                preferencesRepository,
                activityRepository,
                new TrainingPlanValidator(25.0),
                new ObjectMapper());
    }

    @Test
    void savesPlanAndCompletesJobInTheSameOperation() {
        PlanGenerationJob job = job(TrainingJobStatus.PENDING);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(activityRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                eq(USER_ID), any(), any())).thenReturn(List.of());
        when(planRepository.findFirstByUserIdAndWeekStartAndStatusOrderByVersionDesc(
                USER_ID, MONDAY, PlanStatus.ACTIVE)).thenReturn(Optional.empty());
        when(planRepository.findFirstByUserIdAndWeekStartOrderByVersionDesc(USER_ID, MONDAY))
                .thenReturn(Optional.empty());
        when(planRepository.save(any(TrainingPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any(PlanGenerationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TrainingPlanResponseDto response = service.saveWeeklyPlan(
                USER_ID, REQUEST_ID, MONDAY, false, payload());

        assertThat(response.planId()).isNotBlank();
        assertThat(response.weekStart()).isEqualTo(MONDAY);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.workouts()).hasSize(1);
        assertThat(job.getStatus()).isEqualTo(TrainingJobStatus.COMPLETED);
        assertThat(job.getPlanId()).isEqualTo(response.planId());
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void returnsExistingPlanWhenJobWasAlreadyCompleted() {
        PlanGenerationJob job = job(TrainingJobStatus.COMPLETED);
        job.setPlanId("plan-1");
        TrainingPlan existing = plan(1, PlanStatus.ACTIVE);
        existing.setId("plan-1");
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(planRepository.findByIdAndUserId("plan-1", USER_ID)).thenReturn(Optional.of(existing));

        TrainingPlanResponseDto response = service.saveWeeklyPlan(
                USER_ID, REQUEST_ID, MONDAY, true, payload());

        assertThat(response.planId()).isEqualTo("plan-1");
        verify(planRepository, never()).save(any(TrainingPlan.class));
    }

    @Test
    void rejectsSecondPlanForSameWeekWithoutReplace() {
        PlanGenerationJob job = job(TrainingJobStatus.PENDING);
        TrainingPlan existing = plan(1, PlanStatus.ACTIVE);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(activityRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                eq(USER_ID), any(), any())).thenReturn(List.of());
        when(planRepository.findFirstByUserIdAndWeekStartAndStatusOrderByVersionDesc(
                USER_ID, MONDAY, PlanStatus.ACTIVE)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.saveWeeklyPlan(
                USER_ID, REQUEST_ID, MONDAY, false, payload()))
                .isInstanceOf(PlanAlreadyExistsException.class);
    }

    @Test
    void archivesPreviousPlanAndCreatesNextVersionWhenReplaceIsTrue() {
        PlanGenerationJob job = job(TrainingJobStatus.PENDING);
        TrainingPlan existing = plan(1, PlanStatus.ACTIVE);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(activityRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                eq(USER_ID), any(), any())).thenReturn(List.of());
        when(planRepository.findFirstByUserIdAndWeekStartAndStatusOrderByVersionDesc(
                USER_ID, MONDAY, PlanStatus.ACTIVE)).thenReturn(Optional.of(existing));
        when(planRepository.findFirstByUserIdAndWeekStartOrderByVersionDesc(USER_ID, MONDAY))
                .thenReturn(Optional.of(existing));
        when(planRepository.save(any(TrainingPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any(PlanGenerationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TrainingPlanResponseDto response = service.saveWeeklyPlan(
                USER_ID, REQUEST_ID, MONDAY, true, payload());

        assertThat(existing.getStatus()).isEqualTo(PlanStatus.ARCHIVED);
        assertThat(response.version()).isEqualTo(2);
        ArgumentCaptor<TrainingPlan> captor = ArgumentCaptor.forClass(TrainingPlan.class);
        verify(planRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(PlanStatus.ACTIVE);
    }

    @Test
    void cannotSavePlanForAnotherUsersJob() {
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveWeeklyPlan(
                USER_ID, REQUEST_ID, MONDAY, false, payload()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void doesNotMarkCompletedJobAsFailed() {
        PlanGenerationJob job = job(TrainingJobStatus.COMPLETED);
        job.setPlanId("plan-1");
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));

        PlanGenerationJobResponseDto response = service.failJob(USER_ID, REQUEST_ID, "Telegram falló");

        assertThat(response.status()).isEqualTo(TrainingJobStatus.COMPLETED);
        verify(jobRepository, never()).save(any(PlanGenerationJob.class));
    }

    private PlanGenerationJob job(TrainingJobStatus status) {
        return PlanGenerationJob.builder()
                .id(REQUEST_ID)
                .userId(USER_ID)
                .weekStart(MONDAY)
                .status(status)
                .source(TrainingJobSource.MANUAL)
                .attempts(0)
                .build();
    }

    private TrainingPlan plan(int version, PlanStatus status) {
        return TrainingPlan.builder()
                .id("plan-" + version)
                .userId(USER_ID)
                .weekStart(MONDAY)
                .weekEnd(MONDAY.plusDays(6))
                .status(status)
                .version(version)
                .requestId("old-request")
                .build();
    }

    private WeeklyPlanPayload payload() {
        return new WeeklyPlanPayload(
                "Resumen",
                "Notas",
                8000.0,
                60.0,
                List.of(new PlannedWorkoutPayload(
                        MONDAY, WorkoutType.EASY, "Rodaje suave", "Z2",
                        8000.0, 2880.0, 5.4, 2, 60.0, null)),
                "modelo",
                "v1");
    }
}
