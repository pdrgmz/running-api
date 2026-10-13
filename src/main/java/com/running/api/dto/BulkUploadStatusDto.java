package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Estado de la carga asíncrona de un archivo ZIP")
public class BulkUploadStatusDto {

    @Schema(description = "Identificador único de la carga", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String uploadId;

    @Schema(description = "Nombre del archivo ZIP original", example = "activities_backup.zip")
    private String filename;

    @Schema(description = "Estado de la carga: PENDING, PROCESSING, COMPLETED, FAILED", example = "PROCESSING")
    private String status;

    @Schema(description = "Cantidad total de archivos encontrados en el ZIP", example = "50")
    private int totalFiles;

    @Schema(description = "Cantidad de archivos procesados hasta el momento", example = "32")
    private int processedFiles;

    @Schema(description = "Cantidad de archivos pendientes por procesar", example = "18")
    private int pendingFiles;

    @Schema(description = "Cantidad de archivos importados con éxito", example = "30")
    private int successCount;

    @Schema(description = "Cantidad de archivos con error durante la importación", example = "2")
    private int errorCount;

    @Schema(description = "Porcentaje de archivos faltantes (con error) sobre el total", example = "4.0")
    private double percentMissing;

    @Schema(description = "Porcentaje de progreso de la carga", example = "64.0")
    private double progress;

    @Schema(description = "Lista de errores por archivo")
    private List<FileErrorDto> errors;
}
