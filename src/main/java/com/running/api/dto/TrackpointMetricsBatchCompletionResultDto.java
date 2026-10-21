package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen del completado masivo de FC, cadencia, velocidad y distancia de todos los trackpoints del usuario")
public class TrackpointMetricsBatchCompletionResultDto {

    @Schema(description = "Actividades encontradas para el usuario", example = "42")
    private int activitiesFound;

    @Schema(description = "Actividades procesadas correctamente", example = "40")
    private int activitiesProcessed;

    @Schema(description = "Actividades con algún valor completado, descartado o distancia ajustada", example = "30")
    private int activitiesUpdated;

    @Schema(description = "Actividades sin trackpoints", example = "2")
    private int activitiesWithoutTrackpoints;

    @Schema(description = "Actividades que fallaron durante el proceso", example = "0")
    private int activitiesFailed;

    @Schema(description = "Total de trackpoints procesados", example = "150000")
    private long totalTrackpoints;

    @Schema(description = "Puntos cuya frecuencia cardíaca se completó", example = "1500")
    private int heartRateFilled;

    @Schema(description = "Puntos cuya cadencia se completó", example = "800")
    private int cadenceFilled;

    @Schema(description = "Puntos cuya velocidad se completó", example = "400")
    private int speedFilled;

    @Schema(description = "Valores de frecuencia cardíaca descartados por ser inválidos o muy atípicos respecto a sus vecinos", example = "20")
    private int heartRateRemoved;

    @Schema(description = "Valores de cadencia descartados por ser inválidos o muy atípicos respecto a sus vecinos", example = "10")
    private int cadenceRemoved;

    @Schema(description = "Valores de velocidad descartados por ser inválidos o muy atípicos respecto a sus vecinos", example = "30")
    private int speedRemoved;

    @Schema(description = "Total de valores completados entre las tres métricas", example = "2700")
    private int totalFilled;

    @Schema(description = "Total de valores descartados por ser inválidos o muy atípicos", example = "60")
    private int totalRemoved;

    @Schema(description = "Puntos cuya distancia acumulada se ajustó", example = "350")
    private int distanceAdjusted;

    @Schema(description = "Metros descartados por incrementos de distancia imposibles", example = "12500.0")
    private double distanceDiscardedMeters;

    @Builder.Default
    @Schema(description = "Errores por actividad; la clave es el ID de la actividad")
    private Map<String, String> errors = new LinkedHashMap<>();

    @Schema(description = "Mensaje descriptivo del resultado")
    private String message;
}
