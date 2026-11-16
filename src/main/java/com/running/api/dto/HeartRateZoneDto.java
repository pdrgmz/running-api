package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Rango de una zona de frecuencia cardíaca")
public record HeartRateZoneDto(
        @Schema(description = "Número de zona (1-5)", example = "1")
        int zone,

        @Schema(description = "Nombre de la zona", example = "Recuperación")
        String name,

        @Schema(description = "Límite inferior en bpm", example = "95")
        int minBpm,

        @Schema(description = "Límite superior en bpm", example = "114")
        int maxBpm,

        @Schema(description = "Porcentaje inferior respecto a la referencia (FC máx o reserva)", example = "50.0")
        double minPercent,

        @Schema(description = "Porcentaje superior respecto a la referencia (FC máx o reserva)", example = "60.0")
        double maxPercent
) {
}
