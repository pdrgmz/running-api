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

    @Schema(description = "Trackpoints con altitud válida o calculada", example = "190480")
    private long trackpointsWithElevation;

    @Schema(description = "Trackpoints con altitud nula o 0 sin calcular", example = "12421")
    private long trackpointsMissingElevation;

    @Schema(description = "Porcentaje de trackpoints con altitud válida o calculada", example = "93.88")
    private double withElevationPercent;

    @Schema(description = "Porcentaje de trackpoints sin altitud válida ni calculada", example = "6.12")
    private double missingElevationPercent;
}
