package com.running.api.service;

import com.running.api.model.PlanGenerationJob;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.TrainingPreferences;
import com.running.api.repository.PlanGenerationJobRepository;
import com.running.api.repository.TrainingPlanRepository;
import com.running.api.repository.TrainingPreferencesRepository;
import com.running.api.util.TrainingWeekUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cron de generación semanal de planes y reconciliación de jobs caducados.
 * <p>
 * n8n no conoce la lista de usuarios: la API decide qué atletas tienen la
 * generación activada, firma un JWT efímero por cada uno y llama al mismo
 * webhook genérico. Diseñado para una sola instancia; si hay varias réplicas,
 * activa el cron solo en una o usa un lock distribuido.
 */
@Slf4j
@Service
public class TrainingPlanScheduler {

    private final TrainingPreferencesRepository preferencesRepository;
    private final TrainingPlanRepository planRepository;
    private final PlanGenerationJobRepository jobRepository;
    private final TrainingPlanGenerationService generationService;
    private final TrainingPlanService trainingPlanService;
    private final boolean enabled;
    private final long jobTimeoutMinutes;
    private final AtomicBoolean generating = new AtomicBoolean(false);
    private final AtomicBoolean reconciling = new AtomicBoolean(false);

    public TrainingPlanScheduler(
            TrainingPreferencesRepository preferencesRepository,
            TrainingPlanRepository planRepository,
            PlanGenerationJobRepository jobRepository,
            TrainingPlanGenerationService generationService,
            TrainingPlanService trainingPlanService,
            @Value("${app.training.plan.enabled:false}") boolean enabled,
            @Value("${app.training.plan.job-timeout-minutes:30}") long jobTimeoutMinutes) {
        this.preferencesRepository = preferencesRepository;
        this.planRepository = planRepository;
        this.jobRepository = jobRepository;
        this.generationService = generationService;
        this.trainingPlanService = trainingPlanService;
        this.enabled = enabled;
        this.jobTimeoutMinutes = Math.max(5, jobTimeoutMinutes);
    }

    @Scheduled(cron = "${app.training.plan.cron:0 0 18 * * SUN}")
    public void generateWeeklyPlans() {
        if (!enabled) {
            return;
        }
        if (!generating.compareAndSet(false, true)) {
            log.warn("La generación semanal de planes anterior sigue en ejecución; se omite esta ejecución");
            return;
        }

        try {
            List<TrainingPreferences> eligible = preferencesRepository.findByEnabledTrue();
            int triggered = 0;
            for (TrainingPreferences preferences : eligible) {
                Long userId = preferences.getUserId();
                try {
                    ZoneId zone = TrainingWeekUtils.zoneOrDefault(preferences.getTimezone());
                    LocalDate weekStart = TrainingWeekUtils.nextWeekStart(zone);

                    if (planRepository.existsByUserIdAndWeekStart(userId, weekStart)) {
                        log.debug("El usuario {} ya tiene plan para la semana {}", userId, weekStart);
                        continue;
                    }
                    if (jobRepository.existsByUserIdAndWeekStartAndStatusIn(
                            userId, weekStart,
                            List.of(com.running.api.model.TrainingJobStatus.PENDING,
                                    com.running.api.model.TrainingJobStatus.RUNNING,
                                    com.running.api.model.TrainingJobStatus.COMPLETED))) {
                        log.debug("El usuario {} ya tiene un job para la semana {}", userId, weekStart);
                        continue;
                    }

                    PlanGenerationJob job = generationService.createScheduledJob(userId, weekStart);
                    triggered++;
                    log.info("Plan semanal encolado: userId={}, weekStart={}, requestId={}, source={}",
                            userId, weekStart, job.getId(), TrainingJobSource.SCHEDULED);
                } catch (Exception ex) {
                    log.error("No se pudo encolar el plan semanal del usuario {}: {}",
                            userId, ex.getMessage(), ex);
                }
            }
            log.info("Generación semanal de planes: {} jobs encolados de {} usuarios elegibles",
                    triggered, eligible.size());
        } finally {
            generating.set(false);
        }
    }

    @Scheduled(cron = "${app.training.plan.reconcile-cron:0 */10 * * * *}")
    public void reconcileStaleJobs() {
        if (!reconciling.compareAndSet(false, true)) {
            return;
        }
        try {
            trainingPlanService.reconcileStaleJobs(Duration.ofMinutes(jobTimeoutMinutes));
        } catch (Exception ex) {
            log.error("Error reconciliando jobs de plan caducados: {}", ex.getMessage(), ex);
        } finally {
            reconciling.set(false);
        }
    }
}
