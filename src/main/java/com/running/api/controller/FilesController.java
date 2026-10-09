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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Archivos", description = "Endpoints para la importación y exportación de archivos TCX.")
public class FilesController {

    private final FileService fileService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Importar un archivo TCX individual", description = "Parsea e inserta la actividad en la BD y guarda el archivo físico de respaldo.")
    public ResponseEntity<FileUploadResponseDto> uploadSingleTcx(@RequestParam("file") MultipartFile file) throws Exception {
        Activity activity = fileService.saveTcxFile(file); 
        FileUploadResponseDto response = FileUploadResponseDto.builder()
                .message("Carrera importada y respaldada con éxito")
                .activityId(activity.getId())
                .backupPath(activity.getBackupFilePath())
                .totalPoints(activity.getTrackpoints() != null ? activity.getTrackpoints().size() : 0)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/upload-bulk", consumes = "multipart/form-data")
    @Operation(summary = "Importar un archivo ZIP con múltiples archivos TCX dentro", description = "Parsea el ZIP, extrae los archivos .tcx, crea las actividades en la BD y guarda los archivos físicos de respaldo.")
    public ResponseEntity<BulkFileUploadResponseDto> uploadBulkTcx(@RequestParam("zipFile") MultipartFile zipFile) throws Exception {
        BulkFileUploadResponseDto result = fileService.saveBulkTcxFiles(zipFile);
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(result);
    }

    @GetMapping("/export-bulk")
    @Operation(summary = "Exportar datos JSON comprimidos en ZIP")
    public void exportBulkJsonZip(@RequestParam(required = false) List<String> ids, HttpServletResponse response) throws Exception {
        fileService.exportBulkJsonZip(ids, response);
    }

    @GetMapping("/export-tcx-zip")
    @Operation(summary = "Exportar archivos .tcx físicos de respaldo en ZIP")
    public void exportBulkTcxZip(@RequestParam(required = false) List<String> ids, HttpServletResponse response) throws Exception {
        fileService.exportBulkTcxZip(ids, response);
    }
}
