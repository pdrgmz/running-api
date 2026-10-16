package com.running.api.service;

import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.repository.PersonalRecordRepository;
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
