package com.running.api.service;

import com.running.api.exception.AnalysisJobStateException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.model.ActivityAnalysisJob;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.TrainingJobStatus;
import com.running.api.repository.ActivityAnalysisJobRepository;
import com.running.api.repository.ActivityRepository;
import com.running.api.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityAnalysisServiceTest {

    private static final Long USER_ID = 7L;
    private static final String ACTIVITY_ID = "activity-1";
    private static final String REQUEST_ID = "request-123";
    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(USER_ID, "runner@example.com", "USER", Set.of(), null, null);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityAnalysisJobRepository jobRepository;

    private ActivityAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new ActivityAnalysisService(activityRepository, jobRepository);
    }

    @Test
    void createsPendingJobWhenActivityExists() {
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID))
                .thenReturn(Optional.of(activity(null)));
        when(jobRepository.existsByUserIdAndActivityIdAndStatusIn(
                eq(USER_ID), eq(ACTIVITY_ID), anyCollection())).thenReturn(false);
        when(jobRepository.save(any(ActivityAnalysisJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAnalysisJob job = service.createJob(
                PRINCIPAL, ACTIVITY_ID, false, TrainingJobSource.MANUAL);

        assertThat(job.getActivityId()).isEqualTo(ACTIVITY_ID);
        assertThat(job.getUserId()).isEqualTo(USER_ID);
        assertThat(job.getStatus()).isEqualTo(TrainingJobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
    }

    @Test
    void rejectsNewAnalysisWhenActivityAlreadyHasOne() {
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID))
                .thenReturn(Optional.of(activity("{\"summary\":\"ok\"}")));

        assertThatThrownBy(() -> service.createJob(
                PRINCIPAL, ACTIVITY_ID, false, TrainingJobSource.MANUAL))
                .isInstanceOf(AnalysisJobStateException.class)
                .hasMessageContaining("replace=true");
        verify(jobRepository, never()).save(any(ActivityAnalysisJob.class));
    }

    @Test
    void rejectsNewJobWhenAnotherIsActive() {
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID))
                .thenReturn(Optional.of(activity(null)));
        when(jobRepository.existsByUserIdAndActivityIdAndStatusIn(
                eq(USER_ID), eq(ACTIVITY_ID), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.createJob(
                PRINCIPAL, ACTIVITY_ID, false, TrainingJobSource.MANUAL))
                .isInstanceOf(AnalysisJobStateException.class)
                .hasMessageContaining("en curso");
    }

    @Test
    void failsWhenActivityDoesNotExist() {
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createJob(
                PRINCIPAL, ACTIVITY_ID, false, TrainingJobSource.MANUAL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void savesAnalysisInActivityAndCompletesJob() {
        Activity activity = activity(null);
        ActivityAnalysisJob job = job(TrainingJobStatus.PENDING);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any(ActivityAnalysisJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAnalysisJob result = service.saveAnalysis(
                PRINCIPAL, REQUEST_ID, ACTIVITY_ID, "{\"summary\":\"Buen rodaje\"}");

        assertThat(activity.getAiAnalysis()).isEqualTo("{\"summary\":\"Buen rodaje\"}");
        assertThat(result.getStatus()).isEqualTo(TrainingJobStatus.COMPLETED);
        assertThat(result.getAnalysisResult()).isEqualTo("{\"summary\":\"Buen rodaje\"}");
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void rejectsAnalysisForDifferentActivity() {
        ActivityAnalysisJob job = job(TrainingJobStatus.PENDING);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.saveAnalysis(
                PRINCIPAL, REQUEST_ID, "another-activity", "{}"))
                .isInstanceOf(AnalysisJobStateException.class)
                .hasMessageContaining("no coincide");
    }

    @Test
    void marksJobAsFailed() {
        ActivityAnalysisJob job = job(TrainingJobStatus.PENDING);
        when(jobRepository.findByIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(ActivityAnalysisJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActivityAnalysisJob result = service.failJob(PRINCIPAL, REQUEST_ID, "n8n falló");

        assertThat(result.getStatus()).isEqualTo(TrainingJobStatus.FAILED);
        assertThat(result.getLastError()).isEqualTo("n8n falló");
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void listsJobsForUser() {
        ActivityAnalysisJob job = job(TrainingJobStatus.COMPLETED);
        when(jobRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(job));

        assertThat(service.listJobs(PRINCIPAL)).containsExactly(job);
    }

    private Activity activity(String aiAnalysis) {
        return Activity.builder()
                .id(ACTIVITY_ID)
                .userId(USER_ID)
                .aiAnalysis(aiAnalysis)
                .build();
    }

    private ActivityAnalysisJob job(TrainingJobStatus status) {
        return ActivityAnalysisJob.builder()
                .id(REQUEST_ID)
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .status(status)
                .source(TrainingJobSource.MANUAL)
                .attempts(0)
                .build();
    }
}
