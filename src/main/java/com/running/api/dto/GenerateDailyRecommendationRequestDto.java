package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Petición para generar la recomendación de un día")
public record GenerateDailyRecommendationRequestDto(
        @Schema(description = "Día a recomendar. Si se omite, se usa hoy en la zona horaria del atleta",
                example = "2026-11-11")
        LocalDate date,
        @Schema(description = "Si ya existe una recomendación para ese día, la sobrescribe",
                example = "false")
        Boolean replace
) {
}
