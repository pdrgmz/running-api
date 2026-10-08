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
@Schema(description = "Respuesta de carga de entrenamiento de la sesión (TRIMP / HRSS)")
public class TrainingLoadResponseDto {

    @Schema(description = "ID de la carrera", example = "2026-09-05T07:00:00Z")
    private String activityId;

    @Schema(description = "Carga de entrenamiento principal calculada", example = "120.5")
    private Double trainingLoad;

    @Schema(description = "Puntuación TRIMP ponderada por zonas (Edwards)", example = "120.5")
    private Double edwardsTrimp;

    @Schema(description = "Puntuación TRIMP exponencial (Banister)", example = "115.2")
    private Double banisterTrimp;

    @Schema(description = "Heart Rate Stress Score (HRSS)", example = "50.2")
    private Double hrss;

    @Schema(description = "Nivel de esfuerzo estimado", example = "Moderado")
    private String effortLevel;
}
