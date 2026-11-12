package com.running.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.dto.DailyRecommendationPayload;
import com.running.api.dto.DailyRecommendationPayload.WorkoutPayload;
import com.running.api.dto.DailyRecommendationResponseDto;
import com.running.api.dto.UpdateTrainingPreferencesRequestDto;
import com.running.api.exception.GenerationJobStateException;
import com.running.api.exception.RecommendationAlreadyExistsException;
import com.running.api.exception.RecommendationValidationException;
import com.running.api.model.DailyGenerationJob;
import com.running.api.model.DailyRecommendation;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.TrainingJobStatus;
import com.running.api.model.WorkoutType;
import com.running.api.repository.DailyGenerationJobRepository;
import com.running.api.repository.DailyRecommendationRepository;
import com.running.api.repository.TrainingPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyRecommendationServiceTest {

    private static final Long USER_ID = 7L;
    private static final String REQUEST_ID = "request-123";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 11);

    @Mock
    private DailyRecommendationRepository recommendationRepository;

    @Mock
    private DailyGenerationJobRepository jobRepository;

    @Mock
    private TrainingPreferencesRepository preferencesRepository;

    private DailyRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new DailyRecommendationService(
                recommendationRepository,
                jobRepository,
                preferencesRepository,
                new DailyRecommendationValidator(),
                new ObjectMapper().findAndRegisterModules(),
                6);
    }

    @Test
    void savesNewRecommendationAndCompletesJob() {
        DailyGenerationJob job = job(TrainingJobStatus.PENDING);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(recommendationRepository.findByUserIdAndRecommendationDate(USER_ID, DATE))
                .thenReturn(Optional.empty());
        when(recommendationRepository.save(any(DailyRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any(DailyGenerationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DailyRecommendationResponseDto response = service.saveDailyRecommendation(
                USER_ID, REQUEST_ID, DATE, false, payload());

        assertThat(response.date()).isEqualTo(DATE);
        assertThat(response.workout().type()).isEqualTo(WorkoutType.EASY);
        assertThat(response.requestId()).isEqualTo(REQUEST_ID);
        assertThat(job.getStatus()).isEqualTo(TrainingJobStatus.COMPLETED);
        assertThat(job.getRecommendationId()).isEqualTo(response.recommendationId());
    }

    @Test
    void completedJobIsIdempotentAndReturnsStoredRecommendation() {
        DailyGenerationJob job = job(TrainingJobStatus.COMPLETED);
        job.setRecommendationId("rec-1");
        DailyRecommendation existing = recommendation("rec-1");
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(recommendationRepository.findByIdAndUserId("rec-1", USER_ID))
                .thenReturn(Optional.of(existing));

        DailyRecommendationResponseDto response = service.saveDailyRecommendation(
                USER_ID, REQUEST_ID, DATE, false, payload());

        assertThat(response.recommendationId()).isEqualTo("rec-1");
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void rejectsDateThatDoesNotMatchTheJob() {
        DailyGenerationJob job = job(TrainingJobStatus.PENDING);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.saveDailyRecommendation(
                USER_ID, REQUEST_ID, DATE.plusDays(1), false, payload()))
                .isInstanceOf(GenerationJobStateException.class)
                .hasMessageContaining("no coincide");
    }

    @Test
    void rejectsExistingRecommendationWithoutReplace() {
        DailyGenerationJob job = job(TrainingJobStatus.PENDING);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(recommendationRepository.findByUserIdAndRecommendationDate(USER_ID, DATE))
                .thenReturn(Optional.of(recommendation("rec-1")));

        assertThatThrownBy(() -> service.saveDailyRecommendation(
                USER_ID, REQUEST_ID, DATE, false, payload()))
                .isInstanceOf(RecommendationAlreadyExistsException.class);

        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void replaceOverwritesTheSameDay() {
        DailyGenerationJob job = job(TrainingJobStatus.PENDING);
        DailyRecommendation existing = recommendation("rec-1");
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(recommendationRepository.findByUserIdAndRecommendationDate(USER_ID, DATE))
                .thenReturn(Optional.of(existing));
        when(recommendationRepository.save(any(DailyRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any(DailyGenerationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DailyRecommendationResponseDto response = service.saveDailyRecommendation(
                USER_ID, REQUEST_ID, DATE, true, payload());

        assertThat(response.recommendationId()).isEqualTo("rec-1");
        assertThat(response.workout().title()).isEqualTo("Rodaje suave");
        assertThat(response.requestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void createJobRejectsExistingRecommendation() {
        when(recommendationRepository.existsByUserIdAndRecommendationDate(USER_ID, DATE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createJob(
                USER_ID, DATE, false, TrainingJobSource.MANUAL))
                .isInstanceOf(RecommendationAlreadyExistsException.class);
    }

    @Test
    void createJobRejectsAnActiveJobForTheSameDay() {
        when(recommendationRepository.existsByUserIdAndRecommendationDate(USER_ID, DATE))
                .thenReturn(false);
        when(jobRepository.existsByUserIdAndRecommendationDateAndStatusIn(
                eq(USER_ID), eq(DATE), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.createJob(
                USER_ID, DATE, false, TrainingJobSource.MANUAL))
                .isInstanceOf(GenerationJobStateException.class)
                .hasMessageContaining("en curso");
    }

    @Test
    void failJobMarksTheJobAsFailed() {
        DailyGenerationJob job = job(TrainingJobStatus.RUNNING);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(DailyGenerationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.failJob(USER_ID, REQUEST_ID, "n8n falló");

        assertThat(response.status()).isEqualTo(TrainingJobStatus.FAILED);
        assertThat(response.error()).isEqualTo("n8n falló");
    }

    @Test
    void reconcileMarksStaleJobsAsTimeout() {
        DailyGenerationJob job = job(TrainingJobStatus.RUNNING);
        when(jobRepository.findByStatusInAndUpdatedAtBefore(anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of(job));
        when(jobRepository.save(any(DailyGenerationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int reconciled = service.reconcileStaleJobs(Duration.ofMinutes(60));

        assertThat(reconciled).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(TrainingJobStatus.TIMEOUT);
    }

    @Test
    void parseRejectsInvalidJson() {
        assertThatThrownBy(() -> service.parseRecommendationJson("{no es json"))
                .isInstanceOf(RecommendationValidationException.class);
    }

    @Test
    void updatePreferencesRejectsOutOfRangeHour() {
        when(preferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePreferences(USER_ID,
                new UpdateTrainingPreferencesRequestDto(true, "Europe/Madrid", 24, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generationHour");
    }

    private DailyGenerationJob job(TrainingJobStatus status) {
        return DailyGenerationJob.builder()
                .id(REQUEST_ID)
                .userId(USER_ID)
                .recommendationDate(DATE)
                .status(status)
                .source(TrainingJobSource.MANUAL)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private DailyRecommendation recommendation(String id) {
        return DailyRecommendation.builder()
                .id(id)
                .userId(USER_ID)
                .recommendationDate(DATE)
                .requestId("old-request")
                .type(WorkoutType.RECOVERY)
                .title("Recomendación anterior")
                .build();
    }

    private DailyRecommendationPayload payload() {
        return new DailyRecommendationPayload(
                "Rodaje suave de 45 minutos en Z2",
                "El ACWR está estable y no hay carga acumulada",
                "Mantén la respiración cómoda",
                new WorkoutPayload(WorkoutType.EASY, "Rodaje suave", "45 min en Z2",
                        8000.0, 2700.0, 5.6, 2, 50.0, "45 min continuos"),
                "daily-recommendation-v1");
    }
}
