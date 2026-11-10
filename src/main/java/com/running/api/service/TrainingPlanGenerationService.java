package com.running.api.service;

import com.running.api.dto.GenerateTrainingPlanResponseDto;
import com.running.api.dto.PlanGenerationJobResponseDto;
import com.running.api.exception.PlanGenerationException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.PlanGenerationJob;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.User;
import com.running.api.repository.PlanGenerationJobRepository;
import com.running.api.repository.UserRepository;
import com.running.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Orquesta la creación del job, la firma del JWT efímero y el disparo del
 * webhook de n8n. El guardado real lo hará n8n llamando a
 * {@code save_weekly_plan} en el MCP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingPlanGenerationService {

    private final TrainingPlanService trainingPlanService;
    private final N8nPlanWebhookClient webhookClient;
    private final PlanGenerationJobRepository jobRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.training.plan.token-ttl-minutes:45}")
    private long tokenTtlMinutes;

    @Value("${app.training.plan.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.training.plan.retry-backoff-millis:2000}")
    private long retryBackoffMillis;

    public TrainingPlanGenerationService(
            TrainingPlanService trainingPlanService,
            N8nPlanWebhookClient webhookClient,
            PlanGenerationJobRepository jobRepository,
            UserRepository userRepository,
            JwtService jwtService) {
        this.trainingPlanService = trainingPlanService;
        this.webhookClient = webhookClient;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public GenerateTrainingPlanResponseDto generateNow(Long userId, LocalDate weekStart, boolean replace) {
        PlanGenerationJob job = trainingPlanService.createJob(
                userId, weekStart, replace, TrainingJobSource.MANUAL);
        trigger(job, replace);
        return new GenerateTrainingPlanResponseDto(
                job.getId(),
                job.getWeekStart(),
                job.getStatus(),
                "Generación encolada. Consulta el estado en /api/v1/training/generation-jobs/"
                        + job.getId());
    }

    public PlanGenerationJob createScheduledJob(Long userId, LocalDate weekStart) {
        PlanGenerationJob job = trainingPlanService.createJob(
                userId, weekStart, false, TrainingJobSource.SCHEDULED);
        trigger(job, false);
        return job;
    }

    public PlanGenerationJobResponseDto getJob(Long userId, String requestId) {
        return trainingPlanService.getJob(userId, requestId);
    }

    private void trigger(PlanGenerationJob job, boolean replace) {
        User user = userRepository.findById(job.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + job.getUserId()));
        String token = jwtService.generatePlanGenerationToken(
                user, job.getId(), Duration.ofMinutes(Math.max(5, tokenTtlMinutes)));

        RuntimeException lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Persistir el intento antes de llamar al webhook
            job.setAttempts(attempt);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);

            try {
                webhookClient.trigger(token, job.getId(), job.getWeekStart(), replace);
                log.info("Job de plan enviado a n8n (intento {}/{}): requestId={}",
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
            trainingPlanService.failJob(job.getUserId(), job.getId(),
                    lastEx != null ? lastEx.getMessage() : "Error desconocido");
        } catch (Exception failEx) {
            log.warn("No se pudo marcar como FAILED el job {}: {}", job.getId(), failEx.getMessage());
        }
        if (lastEx instanceof PlanGenerationException generationException) {
            throw generationException;
        }
        throw new PlanGenerationException("No se pudo disparar la generación del plan tras "
                + maxAttempts + " intentos: "
                + (lastEx != null ? lastEx.getMessage() : "error desconocido"), lastEx);
    }
}
