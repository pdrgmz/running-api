package com.running.api.service;

import com.running.api.dto.ActivityAnalysisJobResponseDto;
import com.running.api.dto.AnalyzeActivityResponseDto;
import com.running.api.exception.AnalysisException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.ActivityAnalysisJob;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.User;
import com.running.api.repository.ActivityAnalysisJobRepository;
import com.running.api.repository.UserRepository;
import com.running.api.security.JwtService;
import com.running.api.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Orquesta la creación del job, la firma del JWT efímero y el disparo del
 * webhook de n8n para el análisis de una actividad. El guardado real lo hará
 * n8n llamando a {@code save_activity_analysis} en el MCP.
 */
@Slf4j
@Service
public class ActivityAnalysisGenerationService {

    private final ActivityAnalysisService analysisService;
    private final N8nActivityAnalysisWebhookClient webhookClient;
    private final ActivityAnalysisJobRepository jobRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final long tokenTtlMinutes;
    private final int maxAttempts;
    private final long retryBackoffMillis;

    public ActivityAnalysisGenerationService(
            ActivityAnalysisService analysisService,
            N8nActivityAnalysisWebhookClient webhookClient,
            ActivityAnalysisJobRepository jobRepository,
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${app.training.activity-analysis.token-ttl-minutes:45}") long tokenTtlMinutes,
            @Value("${app.training.activity-analysis.max-attempts:3}") int maxAttempts,
            @Value("${app.training.activity-analysis.retry-backoff-millis:2000}") long retryBackoffMillis) {
        this.analysisService = analysisService;
        this.webhookClient = webhookClient;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.tokenTtlMinutes = tokenTtlMinutes;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public AnalyzeActivityResponseDto analyzeNow(Long userId, String activityId, boolean replace) {
        ActivityAnalysisJob job = analysisService.createJob(
                new UserPrincipal(userId, null, null),
                activityId, replace, TrainingJobSource.MANUAL);
        trigger(job, replace);
        return new AnalyzeActivityResponseDto(
                job.getId(),
                job.getActivityId(),
                job.getStatus(),
                "Análisis encolado. Consulta el estado en /api/v1/activities/"
                        + job.getActivityId() + "/analysis/jobs/" + job.getId());
    }

    public ActivityAnalysisJobResponseDto getJob(Long userId, String requestId) {
        ActivityAnalysisJob job = analysisService.getJob(
                new UserPrincipal(userId, null, null), requestId);
        return toJobResponse(job);
    }

    private void trigger(ActivityAnalysisJob job, boolean replace) {
        User user = userRepository.findById(job.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + job.getUserId()));
        String token = jwtService.generateAnalysisToken(
                user, job.getId(), Duration.ofMinutes(Math.max(5, tokenTtlMinutes)));

        RuntimeException lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Persistir el intento antes de llamar al webhook
            job.setAttempts(attempt);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);

            try {
                webhookClient.trigger(token, job.getId(), job.getActivityId(), replace);
                log.info("Job de análisis enviado a n8n (intento {}/{}): requestId={}",
                        attempt, maxAttempts, job.getId());
                return; // éxito
            } catch (RuntimeException ex) {
                lastEx = ex;
                log.warn("Intento {}/{} falló para el job {}: {}",
                        attempt, maxAttempts, job.getId(), ex.getMessage());
                if (attempt < maxAttempts && retryBackoffMillis > 0) {
                    try {
                        Thread.sleep(retryBackoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Agotados los reintentos: marcar el job como FAILED
        try {
            analysisService.failJob(
                    new UserPrincipal(job.getUserId(), null, null),
                    job.getId(),
                    lastEx != null ? lastEx.getMessage() : "Error desconocido");
        } catch (Exception failEx) {
            log.warn("No se pudo marcar como FAILED el job {}: {}", job.getId(), failEx.getMessage());
        }
        if (lastEx instanceof AnalysisException analysisException) {
            throw analysisException;
        }
        throw new AnalysisException("No se pudo disparar el análisis de la actividad tras "
                + maxAttempts + " intentos: "
                + (lastEx != null ? lastEx.getMessage() : "error desconocido"), lastEx);
    }

    private ActivityAnalysisJobResponseDto toJobResponse(ActivityAnalysisJob job) {
        return new ActivityAnalysisJobResponseDto(
                job.getId(),
                job.getUserId(),
                job.getActivityId(),
                job.getStatus(),
                job.getSource() == null ? null : job.getSource().name(),
                job.getAttempts(),
                job.getAnalysisResult(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt());
    }
}
