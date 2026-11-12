package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Contexto completo que recibe n8n para que el LLM recomiende el entrenamiento del día")
public record TrainingContextDto(
        LocalDate date,
        LocalDate today,
        String timezone,
        Preferences preferences,
        SummaryStatsDto summary,
        AcwrStatusDto acwr,
        Integer athleteMaxHeartRate,
        List<RecentActivityDto> recentActivities,
        List<RecentActivityDto> activitiesOnDate,
        List<WeeklySummaryDto> recentWeeks,
        List<RecordResponseDto> personalRecords,
        DailyRecommendationResponseDto recommendationForDate,
        List<DailyRecommendationSummaryDto> recentRecommendations
) {

    public record Preferences(
            boolean configured,
            Boolean enabled,
            String timezone,
            Integer generationHour,
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
}
