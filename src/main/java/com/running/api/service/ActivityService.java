package com.running.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.repository.ActivityRepository;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import com.running.api.model.GlobalSummaryStats;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.running.api.repository.PersonalRecordRepository;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityTrackpointRepository trackpointRepository;
    private final GlobalSummaryStatsRepository summaryStatsRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final BackupStorageService backupStorageService;
    private final AnalyticsService analyticsService;
    private final StatsService statsService;

    @Transactional
    public List<Activity> getAllActivities() {
        List<Activity> list = activityRepository.findAll();
        boolean globalStatsNeedUpdate = false;
        for (Activity activity : list) {
            List<ActivityTrackpoint> points = trackpointRepository.findByActivityIdOrderByTimestampAsc(activity.getId());
            boolean modified = analyticsService.populateMetrics(activity, points);
            if (modified) {
                activityRepository.save(activity);
                globalStatsNeedUpdate = true;
            }
        }
        if (globalStatsNeedUpdate) {
            recalculateGlobalSummaryStats();
        }
        return list;
    }

    @Transactional
    public Optional<Activity> getActivityById(String id) {
        Optional<Activity> activityOpt = activityRepository.findById(id);
        if (activityOpt.isPresent()) {
            Activity activity = activityOpt.get();
            List<ActivityTrackpoint> points = trackpointRepository.findByActivityIdOrderByTimestampAsc(activity.getId());
            boolean modified = analyticsService.populateMetrics(activity, points);
            if (modified) {
                activityRepository.save(activity);
                recalculateGlobalSummaryStats();
            }
        }
        return activityOpt;
    }

    private void recalculateGlobalSummaryStats() {
        summaryStatsRepository.deleteAll();
        GlobalSummaryStats newStats = activityRepository.calculateAggregatedSummary();
        if (newStats == null) {
            newStats = GlobalSummaryStats.builder().id(1L).build();
        }
        summaryStatsRepository.save(newStats);
    }

    public List<ActivityTrackpoint> getActivityTrackpointsById(String id) {
        return trackpointRepository.findByActivityIdOrderByTimestampAsc(id);
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
            } catch (Exception ignored) {}
        }

        activityRepository.delete(activity);

        if (statsService != null) {
            statsService.recalculateStatsAndRecords();
        }

        return true;
    }
}
