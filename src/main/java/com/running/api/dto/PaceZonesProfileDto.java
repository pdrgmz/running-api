package com.running.api.dto;

import com.running.api.model.PaceZoneMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Perfil de zonas de ritmo del corredor")
public record PaceZonesProfileDto(
        @Schema(description = "Ritmo umbral de referencia (segundos por kilómetro)", example = "270.0")
        double thresholdPaceSecondsPerKm,

        @Schema(description = "Ritmo umbral en formato min/km", example = "4:30")
        String thresholdPaceFormatted,

        @Schema(description = "Método de cálculo", example = "THRESHOLD_PACE_PERCENT")
        PaceZoneMethod method,

        @Schema(description = "Zonas Z1 a Z5, de más lenta a más rápida")
        List<PaceZoneDto> zones
) {
}
