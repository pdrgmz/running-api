package com.running.api.dto;

import com.running.api.dto.TrainingPlanResponseDto.WorkoutDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Contexto completo que recibe n8n para que el LLM genere el plan semanal")
public record TrainingContextDto(
        LocalDate weekStart,
        LocalDate today,
        String timezone,
        Preferences preferences,
        SummaryStatsDto summary,
        AcwrStatusDto acwr,
        Integer athleteMaxHeartRate,
        List<RecentActivityDto> recentActivities,
        List<WeeklySummaryDto> recentWeeks,
        List<RecordResponseDto> personalRecords,
        CurrentPlan currentPlan
) {

    public record Preferences(
            boolean configured,
            Boolean enabled,
            Integer daysPerWeek,
            String preferredLongRunDay,
            String notes
    ) {
    }

    public record RecentActivityDto(
            String id,
            LocalDateTime startTime,
            String name,
            Double distanceMeters,
            Double durationSeconds,
            Double avgPaceMinPerKm,
            Integer avgHeartRate,
            Integer maxHeartRate,
            Double trainingLoad,
            String type
    ) {
    }

    public record WeeklySummaryDto(
            LocalDate weekStart,
            LocalDate weekEnd,
            int sessions,
            Double distanceMeters,
            Double durationSeconds,
            Double trainingLoad,
            Double longestRunMeters
    ) {
    }

    public record CurrentPlan(
            String planId,
            LocalDate weekStart,
            LocalDate weekEnd,
            String status,
            Integer version,
            String summary,
            List<WorkoutDto> workouts
    ) {
    }
}
