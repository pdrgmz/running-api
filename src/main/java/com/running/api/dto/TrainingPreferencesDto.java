package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Preferencias para la generación automática de la recomendación diaria")
public record TrainingPreferencesDto(
        boolean configured,
        Boolean enabled,
        String timezone,
        Integer generationHour,
        String notes,
        LocalDateTime updatedAt
) {
}
