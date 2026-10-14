package com.running.api.service;

import com.running.api.dto.BulkUploadStatusDto;
import com.running.api.dto.FileErrorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkUploadStatusTracker {

    private final ConcurrentHashMap<String, BulkUploadStatusDto> uploads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Consumer<BulkUploadStatusDto>>> listeners = new ConcurrentHashMap<>();

    public String createUpload(String filename) {
        String uploadId = UUID.randomUUID().toString();
        BulkUploadStatusDto status = BulkUploadStatusDto.builder()
                .uploadId(uploadId)
                .filename(filename != null ? filename : "unknown")
                .status("PENDING")
                .totalFiles(0)
                .processedFiles(0)
                .pendingFiles(0)
                .successCount(0)
                .errorCount(0)
                .percentMissing(0.0)
                .progress(0.0)
                .errors(new CopyOnWriteArrayList<>())
                .build();
        uploads.put(uploadId, status);
        log.debug("[UPLOAD] Nueva carga creada: uploadId={}, filename={}", uploadId, filename);
        return uploadId;
    }

    public void registerListener(String uploadId, Consumer<BulkUploadStatusDto> listener) {
        listeners.computeIfAbsent(uploadId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unregisterListeners(String uploadId) {
        listeners.remove(uploadId);
    }

    private void notifyListeners(String uploadId) {
        BulkUploadStatusDto status = uploads.get(uploadId);
        if (status != null) {
            List<Consumer<BulkUploadStatusDto>> uploadListeners = listeners.get(uploadId);
            if (uploadListeners != null) {
                for (Consumer<BulkUploadStatusDto> listener : uploadListeners) {
                    try {
                        listener.accept(status);
                    } catch (Exception ignored) {
                        // Listener no disponible, ignorar
                    }
                }
            }
        }
    }

    public void setTotalFiles(String uploadId, int totalFiles) {
        BulkUploadStatusDto status = uploads.get(uploadId);
        if (status != null) {
            status.setTotalFiles(totalFiles);
            status.setPendingFiles(totalFiles);
            recalculatePercentages(status);
            notifyListeners(uploadId);
        }
    }

    public void setStatus(String uploadId, String newStatus) {
        BulkUploadStatusDto status = uploads.get(uploadId);
        if (status != null) {
            String oldStatus = status.getStatus();
            status.setStatus(newStatus);
            log.info("[UPLOAD] Estado cambiado: {} | {} -> {}", uploadId, oldStatus, newStatus);
            notifyListeners(uploadId);
        }
    }

    public void incrementProcessed(String uploadId, boolean success) {
        BulkUploadStatusDto status = uploads.get(uploadId);
        if (status != null) {
            status.setProcessedFiles(status.getProcessedFiles() + 1);
            status.setPendingFiles(status.getTotalFiles() - status.getProcessedFiles());
            if (success) {
                status.setSuccessCount(status.getSuccessCount() + 1);
            } else {
                status.setErrorCount(status.getErrorCount() + 1);
            }
            recalculatePercentages(status);
            notifyListeners(uploadId);
        }
    }

    public void addError(String uploadId, String fileName, String errorMessage) {
        BulkUploadStatusDto status = uploads.get(uploadId);
        if (status != null) {
            status.getErrors().add(FileErrorDto.builder()
                    .fileName(fileName)
                    .errorMessage(errorMessage)
                    .build());
            log.warn("[UPLOAD] Error en archivo: {} | uploadId={} | Error: {}",
                    fileName, uploadId, errorMessage);
            notifyListeners(uploadId);
        }
    }

    public BulkUploadStatusDto getStatus(String uploadId) {
        return uploads.get(uploadId);
    }

    public void removeUpload(String uploadId) {
        uploads.remove(uploadId);
        listeners.remove(uploadId);
    }

    private void recalculatePercentages(BulkUploadStatusDto status) {
        int total = status.getTotalFiles();
        if (total > 0) {
            status.setProgress(Math.round((status.getProcessedFiles() * 100.0 / total) * 100.0) / 100.0);
            status.setPercentMissing(Math.round((status.getErrorCount() * 100.0 / total) * 100.0) / 100.0);
        } else {
            status.setProgress(0.0);
            status.setPercentMissing(0.0);
        }
    }
}
