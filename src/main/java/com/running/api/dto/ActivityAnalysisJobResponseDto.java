package com.running.api.dto;

import com.running.api.model.TrainingJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Estado de un job de análisis de actividad.
 */
@Schema(description = "Estado de un job de análisis de actividad")
public record ActivityAnalysisJobResponseDto(
        String requestId,
        Long userId,
        String activityId,
        TrainingJobStatus status,
        String source,
        Integer attempts,
        String analysisResult,
        String error,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
}
