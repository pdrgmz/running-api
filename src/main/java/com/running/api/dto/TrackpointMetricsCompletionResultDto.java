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
@Schema(description = "Resumen del completado de frecuencia cardíaca, cadencia y velocidad de los trackpoints")
public class TrackpointMetricsCompletionResultDto {

    @Schema(description = "ID de la actividad procesada", example = "2026-09-05T07:00:00Z")
    private String activityId;

    @Schema(description = "Número total de trackpoints de la actividad", example = "3600")
    private int totalTrackpoints;

    @Schema(description = "Puntos cuya frecuencia cardíaca se completó", example = "15")
    private int heartRateFilled;

    @Schema(description = "Puntos cuya cadencia se completó", example = "8")
    private int cadenceFilled;

    @Schema(description = "Puntos cuya velocidad se completó", example = "4")
    private int speedFilled;

    @Schema(description = "Valores de frecuencia cardíaca fuera de rango descartados", example = "2")
    private int heartRateRemoved;

    @Schema(description = "Valores de cadencia fuera de rango descartados", example = "1")
    private int cadenceRemoved;

    @Schema(description = "Valores de velocidad fuera de rango descartados", example = "3")
    private int speedRemoved;

    @Schema(description = "Total de valores completados entre las tres métricas", example = "27")
    private int totalFilled;

    @Schema(description = "Total de valores fuera de rango descartados", example = "6")
    private int totalRemoved;

    @Schema(description = "Puntos cuya distancia acumulada se ajustó", example = "3")
    private int distanceAdjusted;

    @Schema(description = "Metros descartados por incrementos de distancia imposibles", example = "4900.0")
    private double distanceDiscardedMeters;

    @Schema(description = "Mensaje descriptivo del resultado")
    private String message;
}
