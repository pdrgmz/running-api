package com.running.api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.running.api.dto.ElevationCoverageDto;
import com.running.api.dto.TrackpointMetricsBatchCompletionResultDto;
import com.running.api.dto.TrackpointMetricsCompletionResultDto;
import com.running.api.model.Activity;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.projection.ElevationCoverageProjection;
import com.running.api.util.FlatAltitudeDetector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityTrackpointRepository trackpointRepository;
    private final StatsService statsService;
    private final TrackpointCompletionService trackpointCompletionService;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public ElevationCoverageDto getElevationCoverage(Long userId) {
        ElevationCoverageProjection coverage = trackpointRepository.getElevationCoverage(
                userId, TrackpointCompletionService.ALTITUDE_EPSILON);

        long total = coverage == null || coverage.getTotalTrackpoints() == null
                ? 0L
                : coverage.getTotalTrackpoints();
        long withElevation = coverage == null || coverage.getTrackpointsWithElevation() == null
                ? 0L
                : coverage.getTrackpointsWithElevation();

        long flatElevation = trackpointRepository.findFlatElevationPoints(
                        userId,
                        TrackpointCompletionService.ALTITUDE_EPSILON,
                        FlatAltitudeDetector.FLAT_ALTITUDE_PERCENT,
                        FlatAltitudeDetector.MIN_FLAT_ALTITUDE_POINTS)
                .stream()
                .mapToLong(point -> point.getOccurrences() == null ? 0L : point.getOccurrences())
                .sum();

        return buildElevationCoverage(total, withElevation, flatElevation);
    }

    static ElevationCoverageDto buildElevationCoverage(long total, long withElevation,
                                                       long flatElevation) {
        long validWithElevation = Math.min(Math.max(withElevation, 0L), total);
        long validFlatElevation = Math.min(Math.max(flatElevation, 0L), validWithElevation);
        long validElevation = validWithElevation - validFlatElevation;
        long missing = Math.max(0L, total - validElevation);
        double withPercent = total == 0
                ? 0.0
                : roundPercentage(validElevation * 100.0 / total);
        double missingPercent = total == 0
                ? 0.0
                : roundPercentage(100.0 - withPercent);
        double flatPercent = total == 0
                ? 0.0
                : roundPercentage(validFlatElevation * 100.0 / total);

        return ElevationCoverageDto.builder()
                .totalTrackpoints(total)
                .trackpointsWithElevation(validElevation)
                .trackpointsMissingElevation(missing)
                .trackpointsWithFlatElevation(validFlatElevation)
                .withElevationPercent(withPercent)
                .missingElevationPercent(missingPercent)
                .flatElevationPercent(flatPercent)
                .build();
    }

    private static double roundPercentage(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Aplica el completado de FC, cadencia, velocidad y el ajuste de distancia a
     * todas las actividades del usuario. Solo rellena huecos nulos y valores muy
     * alejados de sus vecinos. No modifica la altitud ni las coordenadas.
     * <p>
     * Cada actividad se procesa en su propia transacción: si una falla, las demás
     * continúan y el error se devuelve en el resumen. Al final se recalculan una sola
     * vez las estadísticas globales y los récords personales.
     */
    public TrackpointMetricsBatchCompletionResultDto completeAllActivityMetrics(Long userId) {
        List<String> activityIds = activityRepository.findIdsByUserId(userId);
        if (activityIds.isEmpty()) {
            return TrackpointMetricsBatchCompletionResultDto.builder()
                    .activitiesFound(0)
                    .errors(Map.of())
                    .message("No hay actividades para procesar")
                    .build();
        }

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int processed = 0;
        int updated = 0;
        int withoutTrackpoints = 0;
        long totalTrackpoints = 0L;
        int heartRateFilled = 0;
        int cadenceFilled = 0;
        int speedFilled = 0;
        int heartRateRemoved = 0;
        int cadenceRemoved = 0;
        int speedRemoved = 0;
        int totalFilled = 0;
        int totalRemoved = 0;
        int distanceAdjusted = 0;

        double distanceDiscardedMeters = 0.0;
        Map<String, String> errors = new LinkedHashMap<>();

        for (String activityId : activityIds) {
            try {
                TrackpointMetricsCompletionResultDto result = txTemplate.execute(status -> {
                    Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "La actividad ya no existe o no pertenece al usuario"));
                    return trackpointCompletionService.completeMetrics(activity);
                });

                if (result == null) {
                    errors.put(activityId, "No se pudo procesar la actividad");
                    continue;
                }

                processed++;
                totalTrackpoints += result.getTotalTrackpoints();
                heartRateFilled += result.getHeartRateFilled();
                cadenceFilled += result.getCadenceFilled();
                speedFilled += result.getSpeedFilled();
                heartRateRemoved += result.getHeartRateRemoved();
                cadenceRemoved += result.getCadenceRemoved();
                speedRemoved += result.getSpeedRemoved();
                totalFilled += result.getTotalFilled();
                totalRemoved += result.getTotalRemoved();
                distanceAdjusted += result.getDistanceAdjusted();
                distanceDiscardedMeters += result.getDistanceDiscardedMeters();

                boolean hasChanges = result.getTotalFilled() > 0
                        || result.getTotalRemoved() > 0
                        || result.getDistanceAdjusted() > 0;
                if (result.getTotalTrackpoints() == 0) {
                    withoutTrackpoints++;
                } else if (hasChanges) {
                    updated++;
                }
            } catch (Exception e) {
                String error = e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage()
                        : e.getClass().getSimpleName();
                errors.put(activityId, error);
                log.warn("No se pudieron completar las métricas de la actividad {}: {}", activityId, error);
            }
        }

        if (processed > 0) {
            statsService.recalculateStatsAndRecords(userId);
        }

        return TrackpointMetricsBatchCompletionResultDto.builder()
                .activitiesFound(activityIds.size())
                .activitiesProcessed(processed)
                .activitiesUpdated(updated)
                .activitiesWithoutTrackpoints(withoutTrackpoints)
                .activitiesFailed(errors.size())
                .totalTrackpoints(totalTrackpoints)
                .heartRateFilled(heartRateFilled)
                .cadenceFilled(cadenceFilled)
                .speedFilled(speedFilled)
                .heartRateRemoved(heartRateRemoved)
                .cadenceRemoved(cadenceRemoved)
                .speedRemoved(speedRemoved)
                .totalFilled(totalFilled)
                .totalRemoved(totalRemoved)
                .distanceAdjusted(distanceAdjusted)
                .distanceDiscardedMeters(roundOneDecimal(distanceDiscardedMeters))
                .errors(errors)
                .message(buildBatchMessage(activityIds.size(), processed, updated, withoutTrackpoints,
                        errors.size(), totalFilled, totalRemoved, distanceAdjusted))
                .build();
    }

    private static String buildBatchMessage(int found, int processed, int updated, int withoutTrackpoints,
            int failed, int totalFilled, int totalRemoved,
            int distanceAdjusted) {
        int unchanged = Math.max(0, processed - updated - withoutTrackpoints);

        StringBuilder message = new StringBuilder("Actividades procesadas: ")
                .append(processed)
                .append(" de ")
                .append(found);

        List<String> details = new ArrayList<>();
        if (updated > 0) {
            details.add("actualizadas: " + updated);
        }
        if (unchanged > 0) {
            details.add("sin cambios: " + unchanged);
        }
        if (withoutTrackpoints > 0) {
            details.add("sin trackpoints: " + withoutTrackpoints);
        }
        if (failed > 0) {
            details.add("fallidas: " + failed);
        }
        if (!details.isEmpty()) {
            message.append(" (").append(String.join(", ", details)).append(")");
        }

        List<String> actions = new ArrayList<>();
        if (totalFilled > 0) {
            actions.add("se completaron " + totalFilled + " valores");
        }
        if (totalRemoved > 0) {
            actions.add("se descartaron " + totalRemoved + " valores atípicos");
        }
        if (distanceAdjusted > 0) {
            actions.add("se ajustaron " + distanceAdjusted + " distancias");
        }

        if (actions.isEmpty()) {
            message.append(". No había datos que completar");
        } else {
            message.append(". ").append(String.join(", ", actions));
        }

        return message.toString();
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

}
