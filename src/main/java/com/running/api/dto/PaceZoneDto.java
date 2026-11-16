package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Rango de una zona de ritmo")
public record PaceZoneDto(
        @Schema(description = "Número de zona (1-5)", example = "1")
        int zone,

        @Schema(description = "Nombre de la zona", example = "Recuperación")
        String name,

        @Schema(description = "Ritmo más rápido de la zona (segundos por kilómetro)", example = "390.0")
        double fastestPaceSecondsPerKm,

        @Schema(description = "Ritmo más lento de la zona (segundos por kilómetro)", example = "450.0")
        double slowestPaceSecondsPerKm,

        @Schema(description = "Ritmo más rápido en formato min/km", example = "6:30")
        String fastestPaceFormatted,

        @Schema(description = "Ritmo más lento en formato min/km", example = "7:30")
        String slowestPaceFormatted,

        @Schema(description = "Porcentaje del ritmo umbral en el límite rápido", example = "130.0")
        double minPercentOfThreshold,

        @Schema(description = "Porcentaje del ritmo umbral en el límite lento", example = "150.0")
        double maxPercentOfThreshold
) {
}
