package com.running.api.controller;

import com.running.api.dto.BulkUploadStatusDto;
import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.FileUploadResponseDto;
import com.running.api.model.Activity;
import com.running.api.security.UserPrincipal;
import com.running.api.service.BulkUploadStatusTracker;
import com.running.api.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@Tag(name = "Archivos", description = "Importación y exportación de archivos TCX, GPX y FIT del usuario autenticado")
public class FilesController {

    private final FileService fileService;
    private final BulkUploadStatusTracker bulkUploadStatusTracker;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar un archivo individual (TCX, GPX o FIT)",
               description = "Parsea e inserta la actividad en la base de datos y guarda el archivo físico de respaldo. Solo se aceptan actividades de carrera.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Actividad importada y respaldada"),
            @ApiResponse(responseCode = "400", description = "Archivo inválido, formato no soportado o deporte distinto de running",
                    content = @Content(schema = @Schema(implementation = FileUploadResponseDto.class))),
            @ApiResponse(responseCode = "413", description = "El archivo supera el tamaño máximo permitido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<FileUploadResponseDto> uploadSingleFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Archivo de actividad (.tcx, .gpx o .fit)", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            Activity activity = fileService.saveActivityFile(file, principal.id());
            FileUploadResponseDto response = FileUploadResponseDto.builder()
                    .message("Carrera importada y respaldada con éxito")
                    .activityId(activity.getId())
                    .backupPath(activity.getBackupFilePath())
                    .sourceFileType(activity.getSourceFileType())
                    .totalPoints(activity.getTrackpoints() != null ? activity.getTrackpoints().size() : 0)
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            // Archivo inválido o de un deporte no soportado: es un error del cliente (400).
            log.warn("Archivo rechazado {}: {}", file.getOriginalFilename(), e.getMessage());
            return ResponseEntity.badRequest().body(FileUploadResponseDto.builder()
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            // El resto son fallos internos: no se filtra el detalle al cliente.
            log.error("Error al procesar el archivo {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(FileUploadResponseDto.builder()
                    .message("Error interno al procesar el archivo")
                    .build());
        }
    }

    @PostMapping(value = "/upload-bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar un ZIP con múltiples archivos (TCX, GPX o FIT) de forma asíncrona",
               description = "Parsea el ZIP, extrae los archivos .tcx, .gpx y .fit, crea las actividades en la base de datos y guarda los archivos físicos de respaldo. Devuelve un uploadId para consultar el progreso.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Carga aceptada y en proceso"),
            @ApiResponse(responseCode = "413", description = "El ZIP supera el tamaño máximo permitido",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<BulkUploadStatusDto> uploadBulkFiles(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Archivo ZIP con actividades en su interior", required = true)
            @RequestParam("zipFile") MultipartFile zipFile) throws IOException {
        String filename = zipFile.getOriginalFilename();
        byte[] zipBytes = zipFile.getBytes();

        String uploadId = bulkUploadStatusTracker.createUpload(filename);
        bulkUploadStatusTracker.setStatus(uploadId, "PROCESSING");
        fileService.processBulkUploadAsync(zipBytes, filename, uploadId, principal.id());

        BulkUploadStatusDto status = bulkUploadStatusTracker.getStatus(uploadId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }

    @GetMapping("/upload-status/{uploadId}")
    @Operation(summary = "Consultar el estado de una carga masiva asíncrona",
               description = "Devuelve el progreso actual: porcentaje completado, porcentaje de archivos faltantes y lista de errores por archivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actual de la carga"),
            @ApiResponse(responseCode = "404", description = "No existe una carga con ese uploadId",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<BulkUploadStatusDto> getUploadStatus(
            @Parameter(description = "ID de la carga devuelto por /upload-bulk", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @PathVariable String uploadId) {
        BulkUploadStatusDto status = bulkUploadStatusTracker.getStatus(uploadId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping(value = "/upload-status/{uploadId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream SSE del progreso de carga masiva en tiempo real",
               description = "Conexión Server-Sent Events que emite actualizaciones automáticas del estado de la carga. Se cierra automáticamente al completar o fallar.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Flujo de eventos con el estado de la carga"))
    public SseEmitter streamUploadStatus(
            @Parameter(description = "ID de la carga devuelto por /upload-bulk", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @PathVariable String uploadId) {
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
    @Operation(summary = "Exportar datos JSON comprimidos en ZIP",
               description = "Genera un ZIP con las actividades en formato JSON. Si no se indican ids, exporta todas las del usuario.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Archivo ZIP con los datos",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)))
    public void exportBulkJsonZip(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "IDs de actividades a exportar. Si se omite, se exportan todas")
            @RequestParam(required = false) List<String> ids,
            HttpServletResponse response) throws Exception {
        fileService.exportBulkJsonZip(principal.id(), ids, response);
    }

    @GetMapping("/export-backup-zip")
    @Operation(summary = "Exportar los archivos físicos de respaldo en ZIP",
               description = "Genera un ZIP con los archivos originales de respaldo. Si no se indican ids, exporta todos los del usuario.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Archivo ZIP con los respaldos",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)))
    public void exportBulkBackupZip(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "IDs de actividades a exportar. Si se omite, se exportan todas")
            @RequestParam(required = false) List<String> ids,
            HttpServletResponse response) throws Exception {
        fileService.exportBulkBackupZip(principal.id(), ids, response);
    }
}
