package com.running.api.service;

import com.running.api.dto.ElevationCoverageDto;
import com.running.api.dto.TrackpointCompletionResultDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
