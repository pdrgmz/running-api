package com.running.api.service;

import com.running.api.dto.MetricsRecalculationResponseDto;
import com.running.api.dto.TrainingLoadResponseDto;
import com.running.api.dto.Vo2MaxVamResponseDto;
import com.running.api.model.Activity;
import com.running.api.repository.ActivityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recalcula en bloque las métricas derivadas del pulso de todas las actividades de un usuario.
 *
 * <p>Se usa cuando cambia la lógica de cálculo (por ejemplo, al pasar a referenciar las zonas y
 * el TRIMP contra la FC máxima del atleta en lugar del pico de cada sesión). Los valores se
 * guardan actividad a actividad, y el contexto de persistencia se limpia por lotes para no
 * mantener en memoria la telemetría de todo el historial.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsRecalculationService {

    /** Actividades por lote: equilibra el número de consultas y el pico de memoria. */
    private static final int BATCH_SIZE = 25;

    private final ActivityRepository activityRepository;
    private final AnalyticsService analyticsService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Recalcula training load, VO2Max y VAM de todas las actividades del usuario.
     *
     * @param userId propietario de las actividades
     * @return conteos del proceso y la FC máxima de referencia utilizada
     */
    @Transactional
    public MetricsRecalculationResponseDto recalculateAll(Long userId) {
        long startedAt = System.currentTimeMillis();
        int athleteMaxHr = analyticsService.resolveAthleteMaxHr(userId, null);

        int processed = 0;
        int trainingLoadRecalculated = 0;
        int vo2MaxRecalculated = 0;

        int page = 0;
        Page<Activity> batch;
        do {
            batch = activityRepository.findByUserId(userId,
                    PageRequest.of(page, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "startTime")));

            for (Activity activity : batch.getContent()) {
                TrainingLoadResponseDto trainingLoad =
                        analyticsService.computeTrainingLoadDetails(activity, athleteMaxHr);
                activity.setTrainingLoad(trainingLoad.getTrainingLoad());
                if (trainingLoad.getTrainingLoad() != null) {
                    trainingLoadRecalculated++;
                }

                Vo2MaxVamResponseDto vo2 = analyticsService.computeVo2MaxAndVamDetails(activity, athleteMaxHr);
                activity.setVo2MaxEstimated(vo2.getVo2MaxEstimated());
                activity.setVamKmH(vo2.getVamKmH());
                if (vo2.getVo2MaxEstimated() != null) {
                    vo2MaxRecalculated++;
                }

                processed++;
            }

            // Vaciamos el contexto por lote: si no, la telemetría de todo el historial se queda
            // en la sesión de Hibernate y el proceso crece sin límite con usuarios con muchas carreras.
            entityManager.flush();
            entityManager.clear();

            page++;
        } while (batch.hasNext());

        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("Recalculadas métricas de {} actividades del usuario {} con FC máx {} en {} ms",
                processed, userId, athleteMaxHr, durationMs);

        return MetricsRecalculationResponseDto.builder()
                .activitiesProcessed(processed)
                .trainingLoadRecalculated(trainingLoadRecalculated)
                .vo2MaxRecalculated(vo2MaxRecalculated)
                .athleteMaxHrUsed(athleteMaxHr)
                .durationMs(durationMs)
                .build();
    }
}
