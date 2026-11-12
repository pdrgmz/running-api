package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Violación concreta de una regla de validación")
public record ValidationViolationDto(
        @Schema(description = "Código estable de la violación", example = "PACE_OUT_OF_RANGE")
        String code,
        @Schema(description = "Mensaje legible para el usuario/agente")
        String message
) {
}
