package com.running.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.dto.BulkFileUploadResponseDto;
import com.running.api.exception.ResourceNotFoundException;
import com.running.api.model.Activity;
import com.running.api.repository.ActivityRepository;
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

@Service
@RequiredArgsConstructor
public class FileService {

    private final ActivityRepository activityRepository;
    private final TcxParserService tcxParserService;
    private final BackupStorageService backupStorageService;
    
    private final ObjectMapper objectMapper;

    private final GlobalSummaryStatsRepository summaryStatsRepository;    
    private final ActivityTrackpointRepository trackpointRepository;
    private final StatsService statsService;

   
    public Activity createActivity(Activity activity) {
        Activity saved = activityRepository.save(activity);       
        
        updateStatsOnCreate(saved);
        statsService.checkAndSetPersonalRecords(saved);       
        return saved;
    }

    private void updateStatsOnCreate(Activity a) {

        GlobalSummaryStats stats = getOrCreateStats();

        stats.setTotalActivities(stats.getTotalActivities() + 1);
        stats.setTotalDistanceMeters(stats.getTotalDistanceMeters() + (a.getDistanceMeters() != null ? a.getDistanceMeters() : 0.0));
        stats.setTotalTimeSeconds(stats.getTotalTimeSeconds() + (a.getTotalTimeSeconds() != null ? a.getTotalTimeSeconds() : 0.0));
        stats.setTotalElevationGain(stats.getTotalElevationGain() + (a.getElevationGain() != null ? a.getElevationGain() : 0.0));
        stats.setTotalCalories(stats.getTotalCalories() + (a.getTotalCalories() != null ? a.getTotalCalories() : 0));

        if (a.getMaxHeartRate() != null && a.getMaxHeartRate() > stats.getMaxHeartRateGlobal()) {
            stats.setMaxHeartRateGlobal(a.getMaxHeartRate());
        }

        summaryStatsRepository.save(stats);
        
    }

    private GlobalSummaryStats getOrCreateStats() {
        return summaryStatsRepository.findById(1L)
                .orElseGet(() -> GlobalSummaryStats.builder().id(1L).build());
    }

    public Activity saveTcxFile(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            Activity activity = tcxParserService.parse(is);
            String backupPath = backupStorageService.store(file, activity.getId());
            activity.setBackupFilePath(backupPath);
            return createActivity(activity);
        }
    }
    
    public Activity saveTcxInputStream(InputStream is) throws Exception {
        byte[] bytes = is.readAllBytes();
        Activity activity = tcxParserService.parse(new java.io.ByteArrayInputStream(bytes));
        String backupPath = backupStorageService.store(new java.io.ByteArrayInputStream(bytes), activity.getId());
        activity.setBackupFilePath(backupPath);
        return createActivity(activity);
    }
    
    public BulkFileUploadResponseDto saveBulkTcxFiles(MultipartFile file) {

        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();
        int totalProcessed = 0;
        
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";

        if (filename.toLowerCase().endsWith(".zip")) {
            BulkFileUploadResponseDto zipResult = processZipFile(file);
            successCount += zipResult.getSuccessCount();
            errorCount += zipResult.getErrorCount();
            totalProcessed += zipResult.getTotalFiles();
            if (zipResult.getErrors() != null) {
                errors.addAll(zipResult.getErrors());
            }
        } else {
            totalProcessed++;
            try {
                saveTcxFile(file);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                errors.add("Error en " + filename + ": " + e.getMessage());
            }
        }        

        return BulkFileUploadResponseDto.builder()
                .totalFiles(totalProcessed)
                .successCount(successCount)
                .errorCount(errorCount)
                .errors(errors)
                .build();
    }

    private BulkFileUploadResponseDto processZipFile(MultipartFile file) {
        int successCount = 0;
        int errorCount = 0;
        int totalProcessed = 0;
        List<String> errors = new ArrayList<>();

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                if (entryName.toLowerCase().endsWith(".tcx")) {
                    totalProcessed++;
                    try {
                        byte[] entryData = zis.readAllBytes();
                        saveTcxInputStream(new java.io.ByteArrayInputStream(entryData));
                        successCount++;
                    } catch (Exception e) {
                        errorCount++;
                        errors.add("Error en archivo " + entryName + " dentro de " + file.getOriginalFilename() + ": " + e.getMessage());
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            errorCount++;
            errors.add("Error al descomprimir el archivo ZIP " + file.getOriginalFilename() + ": " + e.getMessage());
        }

        return BulkFileUploadResponseDto.builder()
                .totalFiles(totalProcessed)
                .successCount(successCount)
                .errorCount(errorCount)
                .errors(errors)
                .build();
    }

    public void exportBulkJsonZip(List<String> ids, HttpServletResponse response) throws Exception {
        List<Activity> activities = (ids != null && !ids.isEmpty())
                ? activityRepository.findAllById(ids)
                : activityRepository.findAll();

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"activities_json_export.zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Activity activity : activities) {
                String jsonStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(activity);
                ZipEntry entry = new ZipEntry("activity_" + activity.getId().replaceAll("[:.]", "-") + ".json");
                zos.putNextEntry(entry);
                zos.write(jsonStr.getBytes());
                zos.closeEntry();
            }
            zos.finish();
        }
    }

    public void exportBulkTcxZip(List<String> ids, HttpServletResponse response) throws Exception {
        List<Activity> activities = (ids != null && !ids.isEmpty())
                ? activityRepository.findAllById(ids)
                : activityRepository.findAll();

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"activities_tcx_backup.zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Activity activity : activities) {
                if (activity.getBackupFilePath() != null) {
                    File file = new File(activity.getBackupFilePath());
                    if (file.exists()) {
                        ZipEntry entry = new ZipEntry(file.getName());
                        zos.putNextEntry(entry);
                        Files.copy(file.toPath(), zos);
                        zos.closeEntry();
                    }
                }
            }
            zos.finish();
        }
    }
}
