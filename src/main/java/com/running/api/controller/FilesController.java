package com.running.api.controller;

import com.running.api.dto.BulkUploadStatusDto;
import com.running.api.dto.FileUploadResponseDto;
import com.running.api.model.Activity;
import com.running.api.service.BulkUploadStatusTracker;
import com.running.api.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Archivos", description = "Endpoints para la importación y exportación de archivos TCX, GPX y FIT.")
public class FilesController {

    private final FileService fileService;
    private final BulkUploadStatusTracker bulkUploadStatusTracker;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Importar un archivo individual (TCX, GPX o FIT)", description = "Parsea e inserta la actividad en la BD y guarda el archivo físico de respaldo.")
    public ResponseEntity<FileUploadResponseDto> uploadSingleFile(@RequestParam("file") MultipartFile file) {
        try {
            Activity activity = fileService.saveActivityFile(file);
            FileUploadResponseDto response = FileUploadResponseDto.builder()
                    .message("Carrera importada y respaldada con éxito")
                    .activityId(activity.getId())
                    .backupPath(activity.getBackupFilePath())
                    .sourceFileType(activity.getSourceFileType())
                    .totalPoints(activity.getTrackpoints() != null ? activity.getTrackpoints().size() : 0)
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error al procesar el archivo {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            FileUploadResponseDto errorResponse = FileUploadResponseDto.builder()
                    .message("Error al procesar el archivo: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping(value = "/upload-bulk", consumes = "multipart/form-data")
    @Operation(summary = "Importar un archivo ZIP con múltiples archivos (TCX, GPX o FIT) dentro de forma asíncrona",
               description = "Parsea el ZIP, extrae los archivos .tcx, .gpx y .fit, crea las actividades en la BD y guarda los archivos físicos de respaldo. Retorna un uploadId para consultar el progreso.")
    public ResponseEntity<BulkUploadStatusDto> uploadBulkFiles(@RequestParam("zipFile") MultipartFile zipFile) throws IOException {
        String filename = zipFile.getOriginalFilename();
        byte[] zipBytes = zipFile.getBytes();

        String uploadId = bulkUploadStatusTracker.createUpload(filename);
        bulkUploadStatusTracker.setStatus(uploadId, "PROCESSING");
        fileService.processBulkUploadAsync(zipBytes, filename, uploadId);

        BulkUploadStatusDto status = bulkUploadStatusTracker.getStatus(uploadId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }

    @GetMapping("/upload-status/{uploadId}")
    @Operation(summary = "Consultar el estado de una carga masiva asíncrona",
               description = "Devuelve el progreso actual: % completado, % de archivos faltantes y lista de errores por archivo.")
    public ResponseEntity<BulkUploadStatusDto> getUploadStatus(
            @Parameter(description = "ID de la carga devuelto por /upload-bulk") @PathVariable String uploadId) {
        BulkUploadStatusDto status = bulkUploadStatusTracker.getStatus(uploadId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping(value = "/upload-status/{uploadId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream SSE del progreso de carga masiva en tiempo real",
               description = "Conexión Server-Sent Events que envía actualizaciones automáticas del estado de la carga. Se cierra automáticamente al completar o fallar.")
    public SseEmitter streamUploadStatus(
            @Parameter(description = "ID de la carga devuelto por /upload-bulk") @PathVariable String uploadId) {
        SseEmitter emitter = new SseEmitter(0L); // Sin timeout

        bulkUploadStatusTracker.registerListener(uploadId, status -> {
            try {
                emitter.send(SseEmitter.event()
                        .data(status)
                        .name("upload-status"));
                if ("COMPLETED".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) {
                    emitter.complete();
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> bulkUploadStatusTracker.unregisterListeners(uploadId));
        emitter.onTimeout(() -> bulkUploadStatusTracker.unregisterListeners(uploadId));
        emitter.onError(e -> bulkUploadStatusTracker.unregisterListeners(uploadId));

        return emitter;
    }

    @GetMapping("/export-bulk")
    @Operation(summary = "Exportar datos JSON comprimidos en ZIP")
    public void exportBulkJsonZip(@RequestParam(required = false) List<String> ids, HttpServletResponse response) throws Exception {
        fileService.exportBulkJsonZip(ids, response);
    }

    @GetMapping("/export-backup-zip")
    @Operation(summary = "Exportar archivos físicos de respaldo en ZIP")
    public void exportBulkBackupZip(@RequestParam(required = false) List<String> ids, HttpServletResponse response) throws Exception {
        fileService.exportBulkBackupZip(ids, response);
    }
}
