package com.running.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.dto.BulkFileUploadResponseDto;
import com.running.api.model.Activity;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final String TCX_EXTENSION = ".tcx";
    private static final String GPX_EXTENSION = ".gpx";
    private static final String FIT_EXTENSION = ".fit";

    private final ActivityRepository activityRepository;
    private final TcxParserService tcxParserService;
    private final GpxParserService gpxParserService;
    private final FitParserService fitParserService;
    private final BackupStorageService backupStorageService;
    private final ObjectMapper objectMapper;
    private final GlobalSummaryStatsRepository summaryStatsRepository;
    private final ActivityTrackpointRepository trackpointRepository;
    private final StatsService statsService;
    private final BulkUploadStatusTracker bulkUploadStatusTracker;
    private final PlatformTransactionManager transactionManager;

    @Qualifier("zipExecutor")
    private final Executor zipExecutor;

    @Transactional
    public Activity createActivity(Activity activity) {
        Activity saved = activityRepository.save(activity);
        recalculateAllStats();
        statsService.checkAndSetPersonalRecords(saved);
        return saved;
    }

    @Transactional
    public void recalculateAllStats() {
        GlobalSummaryStats newStats = activityRepository.calculateAggregatedSummary();
        if (newStats == null) {
            newStats = GlobalSummaryStats.builder()
                    .id(1L)
                    .totalActivities(0L)
                    .totalDistanceMeters(0.0)
                    .totalTimeSeconds(0.0)
                    .totalElevationGain(0.0)
                    .totalCalories(0L)
                    .maxHeartRateGlobal(0)
                    .build();
        }
        newStats.setId(1L);
        summaryStatsRepository.save(newStats);
    }

    @Async("taskExecutor")
    public CompletableFuture<Activity> saveActivityFileAsync(MultipartFile file) throws Exception {
        return CompletableFuture.completedFuture(saveActivityFile(file));
    }

    @Async("taskExecutor")
    public CompletableFuture<Activity> saveActivityInputStreamAsync(InputStream is, String fileType) throws Exception {
        return CompletableFuture.completedFuture(saveActivityInputStream(is, fileType));
    }

    @Async("taskExecutor")
    public CompletableFuture<BulkFileUploadResponseDto> saveBulkFilesAsync(MultipartFile file) {
        return CompletableFuture.completedFuture(saveBulkFiles(file));
    }

    @Async("taskExecutor")
    public void processBulkUploadAsync(byte[] zipBytes, String filename, String uploadId) {
        try {
            log.info("[{}] Iniciando carga de {} archivos desde {}", uploadId, filename, zipBytes.length);
            int total = countValidFiles(zipBytes);
            bulkUploadStatusTracker.setTotalFiles(uploadId, total);
            log.info("[{}] Se encontraron {} archivos válidos en el ZIP", uploadId, total);
            processZipFile(zipBytes, filename, uploadId);
            bulkUploadStatusTracker.setStatus(uploadId, "COMPLETED");
            log.info("[{}] Carga completada", uploadId);
        } catch (Exception e) {
            bulkUploadStatusTracker.setStatus(uploadId, "FAILED");
            bulkUploadStatusTracker.addError(uploadId, filename, e.getMessage());
            log.error("[{}] Carga fallida: {}", uploadId, e.getMessage());
        }
    }

    @Transactional
    public Activity saveActivityFile(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String fileType = detectFileType(filename);

        try (InputStream is = file.getInputStream()) {
            Activity activity = parseFile(is, fileType);
            activity.setSourceFileType(fileType);

            String backupPath = backupStorageService.store(file, activity.getId(), fileType != null ? fileType : "file");
            activity.setBackupFilePath(backupPath);

            return createActivity(activity);
        }
    }

    @Transactional
    public Activity saveActivityInputStream(InputStream is, String fileType) throws Exception {
        byte[] bytes = is.readAllBytes();
        Activity activity = parseFile(new ByteArrayInputStream(bytes), fileType);
        activity.setSourceFileType(fileType);

        String backupPath = backupStorageService.store(new ByteArrayInputStream(bytes), activity.getId(), fileType);
        activity.setBackupFilePath(backupPath);

        return createActivity(activity);
    }

    /**
     * Guarda una actividad sin recalcular estadísticas ni records personales.
     * Usado en la importación masiva en paralelo donde los stats se calculan al final.
     */
    private void saveActivityWithoutStats(InputStream is, String fileType) throws Exception {
        byte[] bytes = is.readAllBytes();
        Activity activity = parseFile(new ByteArrayInputStream(bytes), fileType);
        activity.setSourceFileType(fileType);

        String backupPath = backupStorageService.store(new ByteArrayInputStream(bytes), activity.getId(), fileType);
        activity.setBackupFilePath(backupPath);

        activityRepository.save(activity);
    }

    private Activity parseFile(InputStream is, String fileType) throws Exception {
        byte[] bytes = is.readAllBytes();

        // TCX y GPX validan previamente; FIT valida internamente en fitParserService.parse()
        if (!"fit".equals(fileType) && !isRunningActivity(new ByteArrayInputStream(bytes), fileType)) {
            throw new IllegalArgumentException("La actividad no es una carrera (running). Solo se importan actividades de carrera.");
        }

        InputStream parseStream = new ByteArrayInputStream(bytes);
        return switch (fileType) {
            case "tcx" -> tcxParserService.parse(parseStream);
            case "gpx" -> gpxParserService.parse(parseStream);
            case "fit" -> fitParserService.parse(parseStream);
            default -> throw new IllegalArgumentException("Tipo de archivo no soportado: " + fileType);
        };
    }

    private boolean isRunningActivity(InputStream is, String fileType) {
        return switch (fileType) {
            case "tcx" -> {
                try { yield TcxParserService.isRunningActivity(is); }
                catch (Exception e) { yield false; }
            }
            case "gpx" -> {
                try { yield GpxParserService.isRunningActivity(is); }
                catch (Exception e) { yield false; }
            }
            case "fit" -> {
                try { yield FitParserService.isRunningActivity(is); }
                catch (Exception e) { yield false; }
            }
            default -> false;
        };
    }

    private String detectFileType(String filename) {
        if (filename.endsWith(TCX_EXTENSION)) return "tcx";
        if (filename.endsWith(GPX_EXTENSION)) return "gpx";
        if (filename.endsWith(FIT_EXTENSION)) return "fit";
        throw new IllegalArgumentException("Extensión de archivo no soportada: " + filename);
    }

    private int countValidFiles(byte[] zipBytes) throws Exception {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String lowerName = entry.getName().toLowerCase();
                if (lowerName.endsWith(TCX_EXTENSION) || lowerName.endsWith(GPX_EXTENSION) || lowerName.endsWith(FIT_EXTENSION)) {
                    count++;
                }
                zis.closeEntry();
            }
        }
        return count;
    }

    private void processZipFile(byte[] zipBytes, String filename, String uploadId) {
        // 1. Leer todos los entries del ZIP primero (secuencial - ZipInputStream no es thread-safe)
        List<ZipFileData> filesToProcess = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                String lowerName = entryName.toLowerCase();

                if (lowerName.endsWith(TCX_EXTENSION) || lowerName.endsWith(GPX_EXTENSION) || lowerName.endsWith(FIT_EXTENSION)) {
                    try {
                        byte[] entryData = zis.readAllBytes();
                        String fileType = detectFileType(entryName);
                        filesToProcess.add(new ZipFileData(entryName, fileType, entryData));
                    } catch (Exception e) {
                        bulkUploadStatusTracker.incrementProcessed(uploadId, false);
                        bulkUploadStatusTracker.addError(uploadId, entryName, e.getMessage());
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            bulkUploadStatusTracker.setStatus(uploadId, "FAILED");
            bulkUploadStatusTracker.addError(uploadId, filename, e.getMessage());
            return;
        }

        // 2. Procesar archivos en paralelo (sin recalcular stats por archivo)
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ZipFileData fileData : filesToProcess) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    log.debug("[{}] [{}] Procesando archivo: {} ({} bytes)", 
                            uploadId, Thread.currentThread().getName(), fileData.fileName, fileData.data.length);
                    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                    txTemplate.execute(status -> {
                        try {
                            saveActivityWithoutStats(new ByteArrayInputStream(fileData.data), fileData.fileType);
                        } catch (Exception e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                        return null;
                    });
                    log.debug("[{}] [{}] Archivo exitoso: {}", 
                            uploadId, Thread.currentThread().getName(), fileData.fileName);
                    bulkUploadStatusTracker.incrementProcessed(uploadId, true);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null && ex.getCause().getMessage() != null
                            ? ex.getCause().getMessage()
                            : ex.getMessage();
                    log.warn("[{}] [{}] Error en archivo {}: {}", 
                            uploadId, Thread.currentThread().getName(), fileData.fileName, msg);
                    bulkUploadStatusTracker.incrementProcessed(uploadId, false);
                    bulkUploadStatusTracker.addError(uploadId, fileData.fileName, msg);
                }
            }, zipExecutor);
            futures.add(future);
        }

        // Esperar a que todos terminen
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 3. Recalcular estadísticas y records personales UNA SOLA VEZ al final
        log.info("[{}] Recalculando estadísticas finales...", uploadId);
        try {
            recalculateAllStats();
            statsService.recalculateAllPersonalRecords();
            log.info("[{}] Estadísticas y records recalculados", uploadId);
        } catch (Exception e) {
            bulkUploadStatusTracker.addError(uploadId, "final-stats", "Error al recalcular estadísticas finales: " + e.getMessage());
            log.error("[{}] Error al recalcular estadísticas: {}", uploadId, e.getMessage());
        }
    }

    private record ZipFileData(String fileName, String fileType, byte[] data) {}

    @Transactional
    public BulkFileUploadResponseDto saveBulkFiles(MultipartFile file) {
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
                saveActivityFile(file);
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

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                String lowerName = entryName.toLowerCase();

                if (lowerName.endsWith(TCX_EXTENSION) || lowerName.endsWith(GPX_EXTENSION) || lowerName.endsWith(FIT_EXTENSION)) {
                    totalProcessed++;
                    try {
                        byte[] entryData = zis.readAllBytes();
                        String fileType = detectFileType(entryName);
                        saveActivityInputStream(new ByteArrayInputStream(entryData), fileType);
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
                ZipEntry entry = new ZipEntry("activity_" + activity.getId().replace(":", "-").replace(".", "-") + ".json");
                zos.putNextEntry(entry);
                zos.write(jsonStr.getBytes());
                zos.closeEntry();
            }
            zos.finish();
        }
    }

    public void exportBulkBackupZip(List<String> ids, HttpServletResponse response) throws Exception {
        List<Activity> activities = (ids != null && !ids.isEmpty())
                ? activityRepository.findAllById(ids)
                : activityRepository.findAll();

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"activities_backup_export.zip\"");

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
