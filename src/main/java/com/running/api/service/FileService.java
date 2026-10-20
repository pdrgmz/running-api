package com.running.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.running.api.model.Activity;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.GlobalSummaryStatsRepository;
import jakarta.servlet.http.HttpServletResponse;
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
    private final StatsService statsService;
    private final BulkUploadStatusTracker bulkUploadStatusTracker;
    private final TrackpointCompletionService trackpointCompletionService;
    private final PlatformTransactionManager transactionManager;

    private final Executor zipExecutor;

    public FileService(ActivityRepository activityRepository,
                       TcxParserService tcxParserService,
                       GpxParserService gpxParserService,
                       FitParserService fitParserService,
                       BackupStorageService backupStorageService,
                       ObjectMapper objectMapper,
                       GlobalSummaryStatsRepository summaryStatsRepository,
                       StatsService statsService,
                       BulkUploadStatusTracker bulkUploadStatusTracker,
                       TrackpointCompletionService trackpointCompletionService,
                       PlatformTransactionManager transactionManager,
                       @Qualifier("zipExecutor") Executor zipExecutor) {
        this.activityRepository = activityRepository;
        this.tcxParserService = tcxParserService;
        this.gpxParserService = gpxParserService;
        this.fitParserService = fitParserService;
        this.backupStorageService = backupStorageService;
        this.objectMapper = objectMapper;
        this.summaryStatsRepository = summaryStatsRepository;
        this.statsService = statsService;
        this.bulkUploadStatusTracker = bulkUploadStatusTracker;
        this.trackpointCompletionService = trackpointCompletionService;
        this.transactionManager = transactionManager;
        this.zipExecutor = zipExecutor;
    }

    @Transactional
    public Activity createActivity(Activity activity) {
        Activity saved = activityRepository.save(activity);

        // La actividad ya está persistida y gestionada por JPA: completamos su telemetría
        // antes de calcular estadísticas y récords para que estos usen los datos finales.
        // La altitud externa se deja al cron salvo que external-on-import=true.
        trackpointCompletionService.completeAfterImport(saved);

        recalculateAllStats(saved.getUserId());
        statsService.checkAndSetPersonalRecords(saved);
        return saved;
    }

    @Transactional
    public void recalculateAllStats(Long userId) {
        GlobalSummaryStats newStats = activityRepository.calculateAggregatedSummary(userId);
        if (newStats == null) {
            newStats = new GlobalSummaryStats(0L, 0.0, 0.0, 0.0, 0L, 0);
        }
        newStats.setUserId(userId);
        final GlobalSummaryStats stats = newStats;
        summaryStatsRepository.findByUserId(userId).ifPresent(existing -> stats.setId(existing.getId()));
        summaryStatsRepository.save(stats);
    }

    @Async("taskExecutor")
    public void processBulkUploadAsync(byte[] zipBytes, String filename, String uploadId, Long userId) {
        try {
            log.info("[{}] Iniciando carga de {} archivos desde {} (usuario {})", uploadId, filename, zipBytes.length, userId);
            int total = countValidFiles(zipBytes);
            bulkUploadStatusTracker.setTotalFiles(uploadId, total);
            log.info("[{}] Se encontraron {} archivos válidos en el ZIP", uploadId, total);
            processZipFile(zipBytes, filename, uploadId, userId);
            bulkUploadStatusTracker.setStatus(uploadId, "COMPLETED");
            log.info("[{}] Carga completada", uploadId);
        } catch (Exception e) {
            bulkUploadStatusTracker.setStatus(uploadId, "FAILED");
            bulkUploadStatusTracker.addError(uploadId, filename, e.getMessage());
            log.error("[{}] Carga fallida: {}", uploadId, e.getMessage());
        }
    }

    @Transactional
    public Activity saveActivityFile(MultipartFile file, Long userId) throws Exception {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String fileType = detectFileType(filename);

        try (InputStream is = file.getInputStream()) {
            Activity activity = parseFile(is, fileType);
            activity.setSourceFileType(fileType);
            activity.setUserId(userId);

            String backupPath = backupStorageService.store(file, activity.getId(), fileType != null ? fileType : "file");
            activity.setBackupFilePath(backupPath);

            return createActivity(activity);
        }
    }

    /**
     * Guarda una actividad sin recalcular estadísticas ni records personales.
     * Usado en la importación masiva en paralelo donde los stats se calculan al final.
     */
    private void saveActivityWithoutStats(InputStream is, String fileType, Long userId) throws Exception {
        byte[] bytes = is.readAllBytes();
        Activity activity = parseFile(new ByteArrayInputStream(bytes), fileType);
        activity.setSourceFileType(fileType);
        activity.setUserId(userId);

        String backupPath = backupStorageService.store(new ByteArrayInputStream(bytes), activity.getId(), fileType);
        activity.setBackupFilePath(backupPath);

        Activity saved = activityRepository.save(activity);

        // Se completa después de guardar, dentro de la misma transacción del archivo.
        // Los cambios se persisten por dirty checking; las estadísticas globales se
        // recalculan una sola vez al finalizar todo el ZIP.
        trackpointCompletionService.completeAfterImport(saved);
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

    private void processZipFile(byte[] zipBytes, String filename, String uploadId, Long userId) {
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
                            saveActivityWithoutStats(new ByteArrayInputStream(fileData.data), fileData.fileType, userId);
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
            recalculateAllStats(userId);
            statsService.recalculateAllPersonalRecords(userId);
            log.info("[{}] Estadísticas y records recalculados", uploadId);
        } catch (Exception e) {
            bulkUploadStatusTracker.addError(uploadId, "final-stats", "Error al recalcular estadísticas finales: " + e.getMessage());
            log.error("[{}] Error al recalcular estadísticas: {}", uploadId, e.getMessage());
        }
    }

    private record ZipFileData(String fileName, String fileType, byte[] data) {}

    public void exportBulkJsonZip(Long userId, List<String> ids, HttpServletResponse response) throws Exception {
        List<Activity> activities = (ids != null && !ids.isEmpty())
                ? activityRepository.findByIdInAndUserId(ids, userId)
                : activityRepository.findByUserId(userId);

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

    public void exportBulkBackupZip(Long userId, List<String> ids, HttpServletResponse response) throws Exception {
        List<Activity> activities = (ids != null && !ids.isEmpty())
                ? activityRepository.findByIdInAndUserId(ids, userId)
                : activityRepository.findByUserId(userId);

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
