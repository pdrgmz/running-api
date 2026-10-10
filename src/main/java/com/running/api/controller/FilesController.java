package com.running.api.controller;

import com.running.api.dto.BulkFileUploadResponseDto;
import com.running.api.dto.FileUploadResponseDto;
import com.running.api.model.Activity;
import com.running.api.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Archivos", description = "Endpoints para la importación y exportación de archivos TCX, GPX y FIT.")
public class FilesController {

    private final FileService fileService;

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
            e.printStackTrace();
            FileUploadResponseDto errorResponse = FileUploadResponseDto.builder()
                    .message("Error al procesar el archivo: " + e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping(value = "/upload-bulk", consumes = "multipart/form-data")
    @Operation(summary = "Importar un archivo ZIP con múltiples archivos (TCX, GPX o FIT) dentro", description = "Parsea el ZIP, extrae los archivos .tcx, .gpx y .fit, crea las actividades en la BD y guarda los archivos físicos de respaldo.")
    public ResponseEntity<BulkFileUploadResponseDto> uploadBulkFiles(@RequestParam("zipFile") MultipartFile zipFile) throws Exception {
        BulkFileUploadResponseDto result = fileService.saveBulkFiles(zipFile);
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(result);
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
