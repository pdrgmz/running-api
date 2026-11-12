package com.running.api.dto;

import com.running.api.model.WorkoutType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Recomendación diaria con la sesión propuesta para ese día")
public record DailyRecommendationResponseDto(
        String recommendationId,
        Long userId,
        LocalDate date,
        String requestId,
        String summary,
        String analysis,
        String coachNotes,
        WorkoutDto workout,
        String promptVersion,
        LocalDateTime generatedAt
) {

    @Schema(description = "Sesión recomendada")
    public record WorkoutDto(
            WorkoutType type,
            String title,
            String description,
            Double targetDistanceMeters,
            Double targetDurationSeconds,
            Double targetPaceMinPerKm,
            Integer targetHrZone,
            Double targetLoad,
            String mainSet
    ) {
    }
}
