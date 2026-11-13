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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de negocio del análisis de actividad: crea jobs, guarda el análisis
 * en la actividad y consulta el estado de los jobs.
 */
@Slf4j
@Service
public class ActivityAnalysisService {

    private static final List<TrainingJobStatus> ACTIVE_JOB_STATUSES =
            List.of(TrainingJobStatus.PENDING, TrainingJobStatus.RUNNING);

    private final ActivityRepository activityRepository;
    private final ActivityAnalysisJobRepository jobRepository;

    public ActivityAnalysisService(
            ActivityRepository activityRepository,
            ActivityAnalysisJobRepository jobRepository) {
        this.activityRepository = activityRepository;
        this.jobRepository = jobRepository;
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ActivityAnalysisJob getJob(UserPrincipal principal, String requestId) {
        return jobRepository.findByIdAndUserId(requestId, principal.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de análisis no encontrado: " + requestId));
    }

    @Transactional(readOnly = true)
    public List<ActivityAnalysisJob> listJobs(UserPrincipal principal) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(principal.id());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveJob(UserPrincipal principal, String activityId) {
        return jobRepository.existsByUserIdAndActivityIdAndStatusIn(
                principal.id(), activityId, ACTIVE_JOB_STATUSES);
    }

    // ---------------------------------------------------------------------
    // Creación de job
    // ---------------------------------------------------------------------

    @Transactional
    public ActivityAnalysisJob createJob(UserPrincipal principal, String activityId, boolean replace,
                                         TrainingJobSource source) {
        // Verificar que la actividad existe y pertenece al usuario
        Activity activity = activityRepository.findByIdAndUserId(activityId, principal.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Actividad no encontrada: " + activityId));

        if (!replace && activity.getAiAnalysis() != null && !activity.getAiAnalysis().isBlank()) {
            throw new AnalysisJobStateException(
                    "Ya existe un análisis para la actividad " + activityId
                            + ". Usa replace=true para sobrescribirlo.");
        }

        if (jobRepository.existsByUserIdAndActivityIdAndStatusIn(
                principal.id(), activityId, ACTIVE_JOB_STATUSES)) {
            throw new AnalysisJobStateException(
                    "Ya hay un job de análisis en curso para la actividad " + activityId);
        }

        LocalDateTime now = LocalDateTime.now();
        ActivityAnalysisJob job = ActivityAnalysisJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(principal.id())
                .activityId(activityId)
                .status(TrainingJobStatus.PENDING)
                .source(source)
                .attempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return jobRepository.save(job);
    }

    // ---------------------------------------------------------------------
    // Guardado del análisis (llamado por n8n vía MCP)
    // ---------------------------------------------------------------------

    /**
     * Guarda el análisis de n8n en la actividad y marca el job como COMPLETED
     * en la misma transacción. Si cualquiera de las dos escrituras falla, no se
     * persiste ninguna.
     */
    @Transactional
    public ActivityAnalysisJob saveAnalysis(UserPrincipal principal, String requestId,
                                            String activityId, String analysisJson) {
        ActivityAnalysisJob job = jobRepository.findByIdAndUserId(requestId, principal.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de análisis no encontrado: " + requestId));

        if (!job.getActivityId().equals(activityId)) {
            throw new AnalysisJobStateException(
                    "El activityId del job (" + job.getActivityId()
                            + ") no coincide con el solicitado (" + activityId + ")");
        }

        if (job.getStatus() == TrainingJobStatus.COMPLETED) {
            return job;
        }
        if (job.getStatus() == TrainingJobStatus.FAILED
                || job.getStatus() == TrainingJobStatus.TIMEOUT) {
            throw new AnalysisJobStateException("El job ya terminó con estado " + job.getStatus());
        }

        if (job.getStatus() == TrainingJobStatus.PENDING) {
            job.setStatus(TrainingJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
        }
        job.setAttempts(job.getAttempts() == null ? 1 : job.getAttempts() + 1);
        job.setUpdatedAt(LocalDateTime.now());

        Activity activity = activityRepository.findByIdAndUserId(activityId, principal.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Actividad no encontrada: " + activityId));

        activity.setAiAnalysis(analysisJson);
        activityRepository.save(activity);

        job.setStatus(TrainingJobStatus.COMPLETED);
        job.setAnalysisResult(analysisJson);
        job.setLastError(null);
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Análisis de actividad guardado: userId={}, activityId={}, requestId={}",
                principal.id(), activityId, requestId);
        return job;
    }

    // ---------------------------------------------------------------------
    // Fallo de job
    // ---------------------------------------------------------------------

    @Transactional
    public ActivityAnalysisJob failJob(UserPrincipal principal, String requestId, String error) {
        ActivityAnalysisJob job = jobRepository.findByIdAndUserId(requestId, principal.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de análisis no encontrado: " + requestId));

        if (job.getStatus() == TrainingJobStatus.COMPLETED) {
            return job;
        }
        if (job.getStatus() == TrainingJobStatus.FAILED || job.getStatus() == TrainingJobStatus.TIMEOUT) {
            return job;
        }
        job.setStatus(TrainingJobStatus.FAILED);
        job.setLastError(truncate(error == null ? "Error desconocido" : error, 4000));
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
        log.warn("Job de análisis marcado como FAILED: requestId={}, userId={}, error={}",
                requestId, principal.id(), job.getLastError());
        return job;
    }

    @Transactional
    public void deleteJob(UserPrincipal principal, String requestId, boolean force) {
        ActivityAnalysisJob job = jobRepository.findByIdAndUserId(requestId, principal.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de análisis no encontrado: " + requestId));
        requireDeletable(job, force);
        jobRepository.delete(job);
        log.info("Job de análisis eliminado: requestId={}, userId={}, status={}",
                requestId, principal.id(), job.getStatus());
    }

    private static void requireDeletable(ActivityAnalysisJob job, boolean force) {
        if (job.getStatus() == TrainingJobStatus.RUNNING && !force) {
            throw new AnalysisJobStateException(
                    "El job está en ejecución (RUNNING). Espera a que termine o usa force=true");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
