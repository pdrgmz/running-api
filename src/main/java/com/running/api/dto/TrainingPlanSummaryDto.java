package com.running.api.dto;

import com.running.api.model.PlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Resumen de un plan semanal para listados")
public record TrainingPlanSummaryDto(
        String planId,
        LocalDate weekStart,
        LocalDate weekEnd,
        PlanStatus status,
        Integer version,
        String summary,
        Double targetWeeklyDistanceMeters,
        Double targetWeeklyLoad,
        Integer workoutCount,
        LocalDateTime generatedAt
) {
}
