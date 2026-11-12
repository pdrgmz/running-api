package com.running.api.service;

import com.running.api.model.DailyGenerationJob;
import com.running.api.model.TrainingJobSource;
import com.running.api.model.TrainingPreferences;
import com.running.api.repository.DailyGenerationJobRepository;
import com.running.api.repository.DailyRecommendationRepository;
import com.running.api.repository.TrainingPreferencesRepository;
import com.running.api.util.TrainingTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cron de generación diaria de recomendaciones y reconciliación de jobs
 * caducados.
 * <p>
 * El cron se ejecuta cada hora y procesa a los atletas cuya hora local ya ha
 * alcanzado su hora configurada (por defecto las 06:00). Así la generación
 * ocurre por la mañana en la zona horaria de cada usuario, no a una hora fija
 * del servidor. Solo se encola un job por día y usuario.
 * <p>
 * n8n no conoce la lista de usuarios: la API decide qué atletas tienen la
 * generación activada, firma un JWT efímero por cada uno y llama al mismo
 * webhook genérico. Diseñado para una sola instancia; si hay varias réplicas,
 * activa el cron solo en una o usa un lock distribuido.
 */
@Slf4j
@Service
public class DailyRecommendationScheduler {

    private final TrainingPreferencesRepository preferencesRepository;
    private final DailyRecommendationRepository recommendationRepository;
    private final DailyGenerationJobRepository jobRepository;
    private final DailyRecommendationGenerationService generationService;
    private final DailyRecommendationService recommendationService;
    private final boolean enabled;
    private final int defaultGenerationHour;
    private final long jobTimeoutMinutes;
    private final AtomicBoolean generating = new AtomicBoolean(false);
    private final AtomicBoolean reconciling = new AtomicBoolean(false);

    public DailyRecommendationScheduler(
            TrainingPreferencesRepository preferencesRepository,
            DailyRecommendationRepository recommendationRepository,
            DailyGenerationJobRepository jobRepository,
            DailyRecommendationGenerationService generationService,
            DailyRecommendationService recommendationService,
            @Value("${app.training.daily.enabled:false}") boolean enabled,
            @Value("${app.training.daily.default-generation-hour:6}") int defaultGenerationHour,
            @Value("${app.training.daily.job-timeout-minutes:60}") long jobTimeoutMinutes) {
        this.preferencesRepository = preferencesRepository;
        this.recommendationRepository = recommendationRepository;
        this.jobRepository = jobRepository;
        this.generationService = generationService;
        this.recommendationService = recommendationService;
        this.enabled = enabled;
        this.defaultGenerationHour = TrainingTimeUtils.normalizeHour(defaultGenerationHour);
        this.jobTimeoutMinutes = Math.max(5, jobTimeoutMinutes);
    }

    @Scheduled(cron = "${app.training.daily.cron:0 0 * * * *}")
    public void generateDailyRecommendations() {
        if (!enabled) {
            return;
        }
        if (!generating.compareAndSet(false, true)) {
            log.warn("La generación diaria anterior sigue en ejecución; se omite esta ejecución");
            return;
        }

        try {
            List<TrainingPreferences> eligible = preferencesRepository.findByEnabledTrue();
            int triggered = 0;
            for (TrainingPreferences preferences : eligible) {
                Long userId = preferences.getUserId();
                try {
                    ZoneId zone = TrainingTimeUtils.zoneOrDefault(preferences.getTimezone());
                    ZonedDateTime now = ZonedDateTime.now(zone);
                    int generationHour = TrainingTimeUtils.normalizeHour(preferences.getGenerationHour());
                    if (now.getHour() < generationHour) {
                        continue;
                    }

                    LocalDate date = now.toLocalDate();
                    if (recommendationRepository.existsByUserIdAndRecommendationDate(userId, date)) {
                        log.debug("El usuario {} ya tiene recomendación para {}", userId, date);
                        continue;
                    }
                    // Si ya se intentó hoy (aunque fallara), no se reintenta en cada barrido.
                    if (jobRepository.existsByUserIdAndRecommendationDate(userId, date)) {
                        log.debug("El usuario {} ya tiene un job para {}", userId, date);
                        continue;
                    }

                    DailyGenerationJob job = generationService.createScheduledJob(userId, date);
                    triggered++;
                    log.info("Recomendación diaria encolada: userId={}, date={}, requestId={}, source={}",
                            userId, date, job.getId(), TrainingJobSource.SCHEDULED);
                } catch (Exception ex) {
                    log.error("No se pudo encolar la recomendación del usuario {}: {}",
                            userId, ex.getMessage(), ex);
                }
            }
            if (triggered > 0) {
                log.info("Generación diaria: {} jobs encolados de {} usuarios elegibles",
                        triggered, eligible.size());
            }
        } finally {
            generating.set(false);
        }
    }

    @Scheduled(cron = "${app.training.daily.reconcile-cron:0 */10 * * * *}")
    public void reconcileStaleJobs() {
        if (!reconciling.compareAndSet(false, true)) {
            return;
        }
        try {
            recommendationService.reconcileStaleJobs(Duration.ofMinutes(jobTimeoutMinutes));
        } catch (Exception ex) {
            log.error("Error reconciliando jobs de recomendación caducados: {}", ex.getMessage(), ex);
        } finally {
            reconciling.set(false);
        }
    }

    /** Hora de generación configurada, expuesta para diagnóstico. */
    public int defaultGenerationHour() {
        return defaultGenerationHour;
    }
}
