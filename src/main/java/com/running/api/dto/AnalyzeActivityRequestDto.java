package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Petición para lanzar el análisis de una actividad mediante n8n.
 */
@Schema(description = "Petición para lanzar el análisis de una actividad mediante n8n")
public record AnalyzeActivityRequestDto(
        @Schema(description = "Si ya existe un análisis para esta actividad, la sobrescribe",
                example = "false")
        Boolean replace
) {
}
