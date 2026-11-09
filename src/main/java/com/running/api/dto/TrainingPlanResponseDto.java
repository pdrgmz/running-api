package com.running.api.dto;

import com.running.api.model.PlanStatus;
import com.running.api.model.WorkoutType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Plan semanal con sus sesiones")
public record TrainingPlanResponseDto(
        String planId,
        Long userId,
        LocalDate weekStart,
        LocalDate weekEnd,
        PlanStatus status,
        Integer version,
        String requestId,
        String summary,
        String coachNotes,
        Double targetWeeklyDistanceMeters,
        Double targetWeeklyLoad,
        String model,
        String promptVersion,
        LocalDateTime generatedAt,
        java.util.List<WorkoutDto> workouts
) {

    @Schema(description = "Sesión planificada")
    public record WorkoutDto(
            Long id,
            LocalDate date,
            WorkoutType type,
            String title,
            String description,
            Double targetDistanceMeters,
            Double targetDurationSeconds,
            Double targetPaceMinPerKm,
            Integer targetHrZone,
            Double targetLoad,
            String mainSet,
            String status,
            String completedActivityId,
            Integer rpe,
            String feedback
    ) {
    }
}
