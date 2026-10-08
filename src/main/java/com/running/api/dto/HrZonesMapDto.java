package com.running.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mapas de detalle por cada una de las 5 zonas cardíacas")
public class HrZonesMapDto {

    @JsonProperty("Z1_Recovery")
    @Schema(description = "Zona 1 - Recuperación (< 60% FC Máx)")
    private HrZoneDetailDto z1Recovery;

    @JsonProperty("Z2_Endurance")
    @Schema(description = "Zona 2 - Resistencia (60% - 70% FC Máx)")
    private HrZoneDetailDto z2Endurance;

    @JsonProperty("Z3_Tempo")
    @Schema(description = "Zona 3 - Tempo / Aeróbico (70% - 80% FC Máx)")
    private HrZoneDetailDto z3Tempo;

    @JsonProperty("Z4_Threshold")
    @Schema(description = "Zona 4 - Umbral (80% - 90% FC Máx)")
    private HrZoneDetailDto z4Threshold;

    @JsonProperty("Z5_Anaerobic")
    @Schema(description = "Zona 5 - Anaeróbico (> 90% FC Máx)")
    private HrZoneDetailDto z5Anaerobic;
}
