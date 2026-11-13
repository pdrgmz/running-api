package com.running.api.dto;

import com.running.api.model.TrainingJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta al encolar el análisis de una actividad mediante n8n.
 */
@Schema(description = "Respuesta al encolar el análisis de una actividad mediante n8n")
public record AnalyzeActivityResponseDto(
        String requestId,
        String activityId,
        TrainingJobStatus status,
        String message
) {
}
