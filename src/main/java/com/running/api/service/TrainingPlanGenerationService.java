package com.running.api.service;

import com.running.api.dto.GenerateTrainingPlanResponseDto;
import com.running.api.dto.PlanGenerationJobResponseDto;
import com.running.api.exception.PlanGenerationException;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.PlanGenerationJob;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.User;
import com.running.api.repository.UserRepository;
import com.running.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

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
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.training.plan.token-ttl-minutes:45}")
    private long tokenTtlMinutes;

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
        try {
            webhookClient.trigger(token, job.getId(), job.getWeekStart(), replace);
        } catch (RuntimeException ex) {
            try {
                trainingPlanService.failJob(job.getUserId(), job.getId(), ex.getMessage());
            } catch (Exception failEx) {
                log.warn("No se pudo marcar como FAILED el job {}: {}", job.getId(), failEx.getMessage());
            }
            if (ex instanceof PlanGenerationException generationException) {
                throw generationException;
            }
            throw new PlanGenerationException("No se pudo disparar la generación del plan: "
                    + ex.getMessage(), ex);
        }
    }
}
