package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Preferencias para la generación automática de planes")
public record TrainingPreferencesDto(
        boolean configured,
        Boolean enabled,
        String timezone,
        Integer daysPerWeek,
        String preferredLongRunDay,
        String notes,
        LocalDateTime updatedAt
) {
}
