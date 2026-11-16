package com.running.api.dto;

import com.running.api.model.HrZoneMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Perfil de zonas de frecuencia cardíaca del corredor")
public record HeartRateZonesProfileDto(
        @Schema(description = "FC máxima de referencia (bpm)", example = "190")
        int maxHeartRate,

        @Schema(description = "FC de reposo (bpm)", example = "52")
        int restingHeartRate,

        @Schema(description = "Método de cálculo", example = "MAX_HR_PERCENT")
        HrZoneMethod method,

        @Schema(description = "Zonas Z1 a Z5")
        List<HeartRateZoneDto> zones
) {
}
