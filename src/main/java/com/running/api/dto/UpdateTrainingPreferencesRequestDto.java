package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Actualización de preferencias de planificación")
public record UpdateTrainingPreferencesRequestDto(
        @Schema(description = "Activa la generación automática semanal", example = "true")
        Boolean enabled,
        @Schema(description = "Zona horaria IANA", example = "Europe/Madrid")
        String timezone,
        @Schema(description = "Sesiones por semana (1-7)", example = "5")
        Integer daysPerWeek,
        @Schema(description = "Día preferido para el rodaje largo", example = "SATURDAY")
        String preferredLongRunDay,
        @Schema(description = "Notas o restricciones para el entrenador/IA")
        String notes
) {
}
