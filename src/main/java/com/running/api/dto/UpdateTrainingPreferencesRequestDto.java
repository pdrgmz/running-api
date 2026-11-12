package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Actualización de preferencias de la recomendación diaria")
public record UpdateTrainingPreferencesRequestDto(
        @Schema(description = "Activa la generación automática diaria", example = "true")
        Boolean enabled,
        @Schema(description = "Zona horaria IANA", example = "Europe/Madrid")
        String timezone,
        @Schema(description = "Hora local (0-23) a la que se genera la recomendación", example = "6")
        Integer generationHour,
        @Schema(description = "Notas o restricciones para el entrenador/IA")
        String notes
) {
}
