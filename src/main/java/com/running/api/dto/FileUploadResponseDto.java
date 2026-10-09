package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta de la importación de un archivo TCX individual")
public class FileUploadResponseDto {

    @Schema(description = "Mensaje informativo del estado de la subida", example = "Carrera importada y respaldada con éxito")
    private String message;

    @Schema(description = "ID único de la actividad procesada", example = "2026-09-05T07:00:00Z")
    private String activityId;

    @Schema(description = "Ruta absoluta de almacenamiento del respaldo", example = "storage/2026-09-05.tcx")
    private String backupPath;

    @Schema(description = "Total de puntos de telemetría parseados", example = "1250")
    private Integer totalPoints;

    @Schema(description = "Tipo de archivo fuente (tcx, gpx, fit)", example = "tcx")
    private String sourceFileType;
}
