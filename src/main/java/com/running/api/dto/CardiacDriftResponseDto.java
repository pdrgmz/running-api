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
@Schema(description = "Respuesta del análisis de desacople aeróbico (Cardiac Drift)")
public class CardiacDriftResponseDto {

    @Schema(description = "Ratio de eficiencia en la primera mitad (velocidad / FC)", example = "0.035")
    private Double firstHalfEfficiencyRatio;

    @Schema(description = "Ratio de eficiencia en la segunda mitad (velocidad / FC)", example = "0.033")
    private Double secondHalfEfficiencyRatio;

    @Schema(description = "Porcentaje de desacople aeróbico", example = "4.50")
    private String cardiacDriftPercentage;

    @Schema(description = "Diagnóstico/Estado del desacople", example = "Esfuerzo aeróbico estable")
    private String decouplingStatus;

    @Schema(description = "Mensaje informativo si no se pudo calcular el desacople", example = "Insuficientes puntos de telemetría")
    private String status;
}
