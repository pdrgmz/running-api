package com.running.api.dto;

import com.running.api.model.TrainingJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Respuesta al encolar la generación de una recomendación diaria")
public record GenerateDailyRecommendationResponseDto(
        String requestId,
        LocalDate date,
        TrainingJobStatus status,
        String message
) {
}
