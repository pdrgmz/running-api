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
    public Page<Activity> getAllActivities(Pageable pageable) {
        return activityRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Activity> getActivityById(String id) {
        return activityRepository.findById(id);
    }

    public List<ActivityTrackpoint> getActivityTrackpointsById(String id) {
        return getActivityTrackpointsById(id, null);
    }

    public List<ActivityTrackpoint> getActivityTrackpointsById(String id, String resolution) {
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
    public int deleteAllActivities() {
        long count = activityRepository.count();

        trackpointRepository.deleteAll();
        activityRepository.deleteAll();
        personalRecordRepository.deleteAll();
        summaryStatsRepository.deleteAll();

        // Reset global stats to 0
        summaryStatsRepository.save(GlobalSummaryStats.builder()
                .id(1L)
                .totalActivities(0L)
                .totalDistanceMeters(0.0)
                .totalTimeSeconds(0.0)
                .totalElevationGain(0.0)
                .totalCalories(0L)
                .maxHeartRateGlobal(0)
                .build());

        if (backupStorageService != null) {
            backupStorageService.clearStorage();
        }

        return (int) count;
    }

    @Transactional
    public boolean deleteActivityById(String id) {
        Optional<Activity> activityOpt = activityRepository.findById(id);
        if (activityOpt.isEmpty()) {
            return false;
        }

        Activity activity = activityOpt.get();

        if (activity.getBackupFilePath() != null) {
            try {
                Files.deleteIfExists(Path.of(activity.getBackupFilePath()));
            } catch (Exception ignored) {
                // Archivo de respaldo ya no existe o no se puede eliminar
            }
        }

        activityRepository.delete(activity);
        fileService.recalculateAllStats();
        statsService.recalculatePersonalRecords();

        return true;
    }
}
