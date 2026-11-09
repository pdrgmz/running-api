package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Violación concreta de una regla de validación del plan")
public record PlanViolationDto(
        @Schema(description = "Código estable de la violación", example = "WEEKLY_INCREASE_TOO_HIGH")
        String code,
        @Schema(description = "Mensaje legible para el usuario/agente")
        String message
) {
}
