package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cobertura de altitudes de los trackpoints del usuario")
public class ElevationCoverageDto {

    @Schema(description = "Número total de trackpoints", example = "202901")
    private long totalTrackpoints;

    @Schema(description = "Trackpoints con altitud válida o calculada (excluye los de altitud constante sospechosa)", example = "190480")
    private long trackpointsWithElevation;

    @Schema(description = "Trackpoints con altitud nula, 0 sin calcular o constante sospechosa", example = "12421")
    private long trackpointsMissingElevation;

    @Schema(description = "Trackpoints descartados por compartir una altitud constante sospechosa (mismo valor en al menos el 90% de los puntos del dispositivo); están incluidos en los pendientes", example = "3200")
    private long trackpointsWithFlatElevation;

    @Schema(description = "Porcentaje de trackpoints con altitud válida o calculada", example = "93.88")
    private double withElevationPercent;

    @Schema(description = "Porcentaje de trackpoints sin altitud válida ni calculada", example = "6.12")
    private double missingElevationPercent;

    @Schema(description = "Porcentaje de trackpoints con altitud constante sospechosa", example = "1.58")
    private double flatElevationPercent;
}
