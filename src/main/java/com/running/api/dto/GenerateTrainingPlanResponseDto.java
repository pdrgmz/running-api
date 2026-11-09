package com.running.api.dto;

import com.running.api.model.TrainingJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Respuesta al encolar la generación de un plan")
public record GenerateTrainingPlanResponseDto(
        String requestId,
        LocalDate weekStart,
        TrainingJobStatus status,
        String message
) {
}
