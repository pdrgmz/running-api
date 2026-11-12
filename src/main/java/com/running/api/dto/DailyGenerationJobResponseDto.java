package com.running.api.dto;

import com.running.api.model.TrainingJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Estado de un job de generación de recomendación diaria")
public record DailyGenerationJobResponseDto(
        String requestId,
        Long userId,
        LocalDate date,
        TrainingJobStatus status,
        String source,
        Integer attempts,
        String recommendationId,
        String error,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
}
