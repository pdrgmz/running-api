package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Métricas de consistencia y rachas consecutivas de entrenamiento")
public record StreakStatsDto(
    @Schema(description = "Días consecutivos actuales corriendo", example = "5")
    int currentDailyStreak,

    @Schema(description = "Racha récord histórica de días consecutivos corriendo", example = "21")
    int longestDailyStreak,

    @Schema(description = "Semanas consecutivas actuales con al menos una carrera registrada", example = "12")
    int currentWeeklyStreak,

    @Schema(description = "Racha récord histórica de semanas consecutivas activas", example = "48")
    int longestWeeklyStreak
) {}