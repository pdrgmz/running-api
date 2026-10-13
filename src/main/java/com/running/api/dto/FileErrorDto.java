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
@Schema(description = "Error individual de un archivo durante la importación masiva")
public class FileErrorDto {

    @Schema(description = "Nombre del archivo dentro del ZIP que generó el error", example = "activity_2024-01-15.tcx")
    private String fileName;

    @Schema(description = "Mensaje de error descriptivo", example = "La actividad no es una carrera (running). Solo se importan actividades de carrera.")
    private String errorMessage;
}
