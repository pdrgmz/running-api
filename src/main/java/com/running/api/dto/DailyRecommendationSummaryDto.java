package com.running.api.dto;

import com.running.api.model.WorkoutType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Resumen de una recomendación diaria para listados e histórico")
public record DailyRecommendationSummaryDto(
        String recommendationId,
        LocalDate date,
        WorkoutType type,
        String title,
        Double targetDistanceMeters,
        Double targetDurationSeconds,
        Double targetPaceMinPerKm,
        Integer targetHrZone,
        Double targetLoad,
        String summary,
        LocalDateTime generatedAt
) {
}
