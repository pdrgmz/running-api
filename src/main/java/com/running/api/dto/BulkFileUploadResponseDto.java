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
@Schema(description = "Respuesta de la importación masiva de archivos TCX / ZIP")
public class BulkFileUploadResponseDto {

    @Schema(description = "Cantidad total de archivos procesados", example = "10")
    private Integer totalFiles;

    @Schema(description = "Cantidad de actividades importadas con éxito", example = "8")
    private Integer successCount;

    @Schema(description = "Cantidad de archivos con error durante la importación", example = "2")
    private Integer errorCount;

    @Schema(description = "Lista detallada de mensajes de error")
    private List<String> errors;
}
