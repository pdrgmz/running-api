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
@Schema(description = "Resumen acumulado de estadísticas del usuario")
public class SummaryStatsDto {

    @Schema(description = "Total de actividades", example = "128")
    private Long totalActivities;

    @Schema(description = "Distancia total en metros", example = "1532000.0")
    private Double totalDistanceMeters;

    @Schema(description = "Distancia total en kilómetros", example = "1532.0")
    private Double totalDistanceKm;

    @Schema(description = "Tiempo total en segundos", example = "486000.0")
    private Double totalTimeSeconds;

    @Schema(description = "Tiempo total formateado (HH:MM:SS)", example = "135:00:00")
    private String totalTimeFormatted;

    @Schema(description = "Desnivel positivo total en metros", example = "12450.0")
    private Double totalElevationGain;

    @Schema(description = "Calorías totales", example = "98000")
    private Integer totalCalories;

    @Schema(description = "Distancia media por actividad en kilómetros", example = "11.97")
    private Double avgDistanceKm;

    @Schema(description = "Frecuencia cardíaca máxima histórica (bpm)", example = "192")
    private Integer maxHeartRateGlobal;
}
