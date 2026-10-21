package com.running.api.service;

import com.running.api.dto.ElevationCoverageDto;
import com.running.api.dto.TrackpointCompletionResultDto;
import com.running.api.dto.TrackpointMetricsBatchCompletionResultDto;
import com.running.api.dto.TrackpointMetricsCompletionResultDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.repository.PersonalRecordRepository;
import com.running.api.repository.projection.ElevationCoverageProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityTrackpointRepository trackpointRepository;
    private final GlobalSummaryStatsRepository summaryStatsRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final BackupStorageService backupStorageService;
    private final FileService fileService;
    private final StatsService statsService;
    private final TrackpointCompletionService trackpointCompletionService;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public Page<Activity> getAllActivities(Long userId, Pageable pageable) {
        return activityRepository.findByUserId(userId, pageable);
    }

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

        return buildElevationCoverage(total, withElevation);
    }

    static ElevationCoverageDto buildElevationCoverage(long total, long withElevation) {
        long validWithElevation = Math.min(Math.max(withElevation, 0L), total);
        long missing = Math.max(0L, total - validWithElevation);
        double withPercent = total == 0
                ? 0.0
                : roundPercentage(validWithElevation * 100.0 / total);
        double missingPercent = total == 0
                ? 0.0
                : roundPercentage(100.0 - withPercent);

        return ElevationCoverageDto.builder()
                .totalTrackpoints(total)
                .trackpointsWithElevation(validWithElevation)
                .trackpointsMissingElevation(missing)
                .withElevationPercent(withPercent)
                .missingElevationPercent(missingPercent)
                .build();
    }

    private static double roundPercentage(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @Transactional(readOnly = true)
    public Optional<Activity> getActivityById(Long userId, String id) {
        return activityRepository.findByIdAndUserId(id, userId);
    }

    public List<ActivityTrackpoint> getActivityTrackpointsById(Long userId, String id) {
        return getActivityTrackpointsById(userId, id, null);
    }

    public List<ActivityTrackpoint> getActivityTrackpointsById(Long userId, String id, String resolution) {
        // Verifica la propiedad antes de exponer la telemetría.
        if (activityRepository.findByIdAndUserId(id, userId).isEmpty()) {
            return List.of();
        }
        List<ActivityTrackpoint> points = trackpointRepository.findByActivityIdOrderByTimestampAsc(id);
        if (points.isEmpty() || resolution == null || resolution.trim().isEmpty()) {
            return points;
        }
        return downsampleTrackpoints(points, resolution);
    }

    /**
     * Completa los huecos de telemetría de una actividad ya almacenada y recalcula sus
     * estadísticas agregadas. Los trackpoints se actualizan por dirty checking dentro de
     * la transacción.
     */
    @Transactional
    public Optional<TrackpointCompletionResultDto> completeActivityTrackpoints(Long userId, String id) {
        Optional<Activity> activityOpt = activityRepository.findByIdAndUserId(id, userId);
        if (activityOpt.isEmpty()) {
            return Optional.empty();
        }

        Activity activity = activityOpt.get();
        TrackpointCompletionResultDto result = trackpointCompletionService.complete(activity);

        // La actividad ya está gestionada por JPA; los cambios se persisten al hacer commit.
        // El resumen global puede cambiar si se completó la altitud o la frecuencia cardíaca.
        fileService.recalculateAllStats(userId);

        return Optional.of(result);
    }

    /**
     * Completa únicamente la frecuencia cardíaca, la cadencia y la velocidad de una actividad
     * ya almacenada. No modifica las coordenadas ni la altitud, por lo que no consume cuota
     * de proveedores externos.
     */
    @Transactional
    public Optional<TrackpointMetricsCompletionResultDto> completeActivityMetrics(Long userId, String id) {
        Optional<Activity> activityOpt = activityRepository.findByIdAndUserId(id, userId);
        if (activityOpt.isEmpty()) {
            return Optional.empty();
        }

        Activity activity = activityOpt.get();
        TrackpointMetricsCompletionResultDto result = trackpointCompletionService.completeMetrics(activity);

        // La FC máxima agregada del usuario puede cambiar; el resto de métricas no.
        fileService.recalculateAllStats(userId);

        return Optional.of(result);
    }

    /**
     * Aplica el completado de FC, cadencia, velocidad y el ajuste de distancia a todas las
     * actividades del usuario. No modifica la altitud ni las coordenadas.
     * <p>
     * Cada actividad se procesa en su propia transacción: si una falla, las demás continúan
     * y el error se devuelve en el resumen. Al final se recalculan una sola vez las
     * estadísticas globales y los récords personales.
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
            actions.add("se descartaron " + totalRemoved + " valores fuera de rango");
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

    private List<ActivityTrackpoint> downsampleTrackpoints(List<ActivityTrackpoint> points, String resolution) {
        int step;
        switch (resolution.trim().toLowerCase()) {
            case "low":
                step = 10;
                break;
            case "medium":
                step = 5;
                break;
            case "high":
                step = 2;
                break;
            default:
                return points;
        }

        if (points.size() <= step) {
            return points;
        }

        List<ActivityTrackpoint> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }

        ActivityTrackpoint lastPoint = points.get(points.size() - 1);
        if (!sampled.isEmpty() && sampled.get(sampled.size() - 1) != lastPoint) {
            sampled.add(lastPoint);
        }
        return sampled;
    }

    @Transactional
    public int deleteAllActivities(Long userId) {
        List<Activity> activities = activityRepository.findByUserId(userId);
        int count = activities.size();

        // Elimina los ficheros de respaldo del usuario (no toda la carpeta compartida).
        for (Activity activity : activities) {
            deleteBackupFile(activity);
        }

        // El borrado de actividades propaga a sus trackpoints (cascade + orphanRemoval).
        activityRepository.deleteAll(activities);
        personalRecordRepository.deleteByUserId(userId);
        summaryStatsRepository.deleteByUserId(userId);

        // Reinicia el resumen del usuario a cero.
        summaryStatsRepository.save(GlobalSummaryStats.builder()
                .userId(userId)
                .totalActivities(0L)
                .totalDistanceMeters(0.0)
                .totalTimeSeconds(0.0)
                .totalElevationGain(0.0)
                .totalCalories(0L)
                .maxHeartRateGlobal(0)
                .build());

        return count;
    }

    @Transactional
    public boolean deleteActivityById(Long userId, String id) {
        Optional<Activity> activityOpt = activityRepository.findByIdAndUserId(id, userId);
        if (activityOpt.isEmpty()) {
            return false;
        }

        Activity activity = activityOpt.get();
        deleteBackupFile(activity);

        activityRepository.delete(activity);
        fileService.recalculateAllStats(userId);
        statsService.recalculatePersonalRecords(userId);

        return true;
    }

    private void deleteBackupFile(Activity activity) {
        if (activity.getBackupFilePath() != null) {
            try {
                Files.deleteIfExists(Path.of(activity.getBackupFilePath()));
            } catch (Exception ignored) {
                // Archivo de respaldo ya no existe o no se puede eliminar
            }
        }
    }
}
