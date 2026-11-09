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
@Schema(description = "Resumen del recálculo masivo de métricas derivadas de las actividades del usuario")
public class MetricsRecalculationResponseDto {

    @Schema(description = "Número de actividades procesadas", example = "107")
    private int activitiesProcessed;

    @Schema(description = "Actividades cuyo training load se pudo recalcular", example = "107")
    private int trainingLoadRecalculated;

    @Schema(description = "Actividades cuyo VO2Max y VAM se pudieron recalcular", example = "104")
    private int vo2MaxRecalculated;

    @Schema(description = "FC máxima del atleta usada como referencia en todos los cálculos", example = "200")
    private int athleteMaxHrUsed;

    @Schema(description = "Duración del proceso en milisegundos", example = "1840")
    private long durationMs;
}
