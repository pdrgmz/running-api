package com.running.api.service;

import com.running.api.dto.DailyGenerationJobResponseDto;
import com.running.api.dto.GenerateDailyRecommendationResponseDto;
import com.running.api.exception.GenerationException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.DailyGenerationJob;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.User;
import com.running.api.repository.DailyGenerationJobRepository;
import com.running.api.repository.UserRepository;
import com.running.api.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Orquesta la creación del job, la firma del JWT efímero y el disparo del
 * webhook de n8n. El guardado real lo hará n8n llamando a
 * {@code save_daily_recommendation} en el MCP.
 */
@Slf4j
@Service
public class DailyRecommendationGenerationService {

    private final DailyRecommendationService recommendationService;
    private final N8nDailyWebhookClient webhookClient;
    private final DailyGenerationJobRepository jobRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final long tokenTtlMinutes;
    private final int maxAttempts;
    private final long retryBackoffMillis;

    public DailyRecommendationGenerationService(
            DailyRecommendationService recommendationService,
            N8nDailyWebhookClient webhookClient,
            DailyGenerationJobRepository jobRepository,
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${app.training.daily.token-ttl-minutes:45}") long tokenTtlMinutes,
            @Value("${app.training.daily.max-attempts:3}") int maxAttempts,
            @Value("${app.training.daily.retry-backoff-millis:2000}") long retryBackoffMillis) {
        this.recommendationService = recommendationService;
        this.webhookClient = webhookClient;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.tokenTtlMinutes = tokenTtlMinutes;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public GenerateDailyRecommendationResponseDto generateNow(Long userId, LocalDate date, boolean replace) {
        LocalDate effectiveDate = date == null ? LocalDate.now(recommendationService.zoneFor(userId)) : date;
        DailyGenerationJob job = recommendationService.createJob(
                userId, effectiveDate, replace, TrainingJobSource.MANUAL);
        trigger(job, replace);
        return new GenerateDailyRecommendationResponseDto(
                job.getId(),
                job.getRecommendationDate(),
                job.getStatus(),
                "Generación encolada. Consulta el estado en /api/v1/training/generation-jobs/"
                        + job.getId());
    }

    public DailyGenerationJob createScheduledJob(Long userId, LocalDate date) {
        DailyGenerationJob job = recommendationService.createJob(
                userId, date, false, TrainingJobSource.SCHEDULED);
        trigger(job, false);
        return job;
    }

    public DailyGenerationJobResponseDto getJob(Long userId, String requestId) {
        return recommendationService.getJob(userId, requestId);
    }

    private void trigger(DailyGenerationJob job, boolean replace) {
        User user = userRepository.findById(job.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + job.getUserId()));
        String token = jwtService.generateGenerationToken(
                user, job.getId(), Duration.ofMinutes(Math.max(5, tokenTtlMinutes)));

        RuntimeException lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Persistir el intento antes de llamar al webhook
            job.setAttempts(attempt);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);

            try {
                webhookClient.trigger(token, job.getId(), job.getRecommendationDate(), replace);
                log.info("Job de recomendación enviado a n8n (intento {}/{}): requestId={}",
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
            recommendationService.failJob(job.getUserId(), job.getId(),
                    lastEx != null ? lastEx.getMessage() : "Error desconocido");
        } catch (Exception failEx) {
            log.warn("No se pudo marcar como FAILED el job {}: {}", job.getId(), failEx.getMessage());
        }
        if (lastEx instanceof GenerationException generationException) {
            throw generationException;
        }
        throw new GenerationException("No se pudo disparar la generación de la recomendación tras "
                + maxAttempts + " intentos: "
                + (lastEx != null ? lastEx.getMessage() : "error desconocido"), lastEx);
    }
}
