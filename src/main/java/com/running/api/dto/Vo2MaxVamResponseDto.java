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
@Schema(description = "Respuesta de estimación de VO2Max, VAM y predicción de tiempos")
public class Vo2MaxVamResponseDto {

    @Schema(description = "ID de la carrera", example = "2026-09-05T07:00:00Z")
    private String activityId;

    @Schema(description = "Estimación del VO2Max en mL/kg/min", example = "48.5")
    private Double vo2MaxEstimated;

    @Schema(description = "Velocidad Aeróbica Máxima (VAM) en km/h", example = "13.86")
    private Double vamKmH;

    @Schema(description = "Categoría de nivel de condición física", example = "Intermedio / Bueno")
    private String fitnessCategory;

    @Schema(description = "Predicción de tiempos de carrera para 5k, 10k, 21k y 42k")
    private RacePredictionsDto predictedRaceTimes;
}
