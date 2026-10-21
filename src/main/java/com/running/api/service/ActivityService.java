package com.running.api.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.running.api.dto.TrackpointCompletionResultDto;
import com.running.api.dto.TrackpointMetricsCompletionResultDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.repository.PersonalRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    /** Tamaño de lote del vaciado, para poder informar del progreso mientras borra. */
    private static final int DELETE_BATCH_SIZE = 10;

    private final ActivityRepository activityRepository;
    private final ActivityTrackpointRepository trackpointRepository;
    private final GlobalSummaryStatsRepository summaryStatsRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final FileService fileService;
    private final StatsService statsService;
    private final TrackpointCompletionService trackpointCompletionService;
    private final ActivityDeletionStatusTracker deletionStatusTracker;

    @Transactional(readOnly = true)
    public Page<Activity> getAllActivities(Long userId, Pageable pageable) {
        return activityRepository.findByUserId(userId, pageable);
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
     * Completa los huecos de telemetría de una actividad ya almacenada y recalcula
     * sus
     * estadísticas agregadas. Los trackpoints se actualizan por dirty checking
     * dentro de
     * la transacción.
     */
    @Transactional

    public Optional<TrackpointCompletionResultDto> completeActivityTrackpoints(
            Long userId, String id) {
        Optional<Activity> activityOpt = activityRepository.findByIdAndUserId(id, userId);
        if (activityOpt.isEmpty()) {
            return Optional.empty();
        }

        Activity activity = activityOpt.get();
        TrackpointCompletionResultDto result = trackpointCompletionService.complete(activity);

        // La actividad ya está gestionada por JPA; los cambios se persisten al hacer
        // commit.
        // El resumen global puede cambiar si se completó la altitud o la frecuencia
        // cardíaca.
        fileService.recalculateAllStats(userId);

        return Optional.of(result);
        //
    }
    //

    /**
     * Completa únicamente la frecuencia cardíaca, la cadencia y la velocidad de una
     * actividad
     * ya almacenada. No modifica las coordenadas ni la altitud, por lo que no
     * consume cuota
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

    /**
     * Vacía las actividades y estadísticas del usuario en segundo plano. El progreso se
     * consulta con el {@code deleteId} en {@link ActivityDeletionStatusTracker}.
     * <p>
     * El borrado es todo o nada: si algo falla, la transacción hace rollback y el estado
     * queda como {@code FAILED}.
     */
    @Async("taskExecutor")
    @Transactional
    public void deleteAllActivities(Long userId, String deleteId) {
        try {
            List<Activity> activities = activityRepository.findByUserId(userId);
            int total = activities.size();
            deletionStatusTracker.markProcessing(deleteId, total);

            // Elimina los ficheros de respaldo del usuario (no toda la carpeta compartida).
            for (Activity activity : activities) {
                deleteBackupFile(activity);
            }

            // El borrado de actividades propaga a sus trackpoints (cascade + orphanRemoval).
            // Se hace por lotes para poder informar del progreso.
            int deleted = 0;
            for (int i = 0; i < total; i += DELETE_BATCH_SIZE) {
                List<Activity> batch = activities.subList(i, Math.min(i + DELETE_BATCH_SIZE, total));
                activityRepository.deleteAll(batch);
                deleted += batch.size();
                deletionStatusTracker.updateProgress(deleteId, deleted);
            }

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

            deletionStatusTracker.markCompleted(deleteId, total);
        } catch (RuntimeException ex) {
            log.error("Vaciado de actividades del usuario {} fallido: {}", userId, ex.getMessage());
            deletionStatusTracker.markFailed(deleteId, ex.getMessage());
            // Se relanza para que la transacción haga rollback: el vaciado es todo o nada.
            throw ex;
        }
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

        //
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

//