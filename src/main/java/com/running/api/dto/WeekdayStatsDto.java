package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;

@Schema(description = "Agregación de métricas de rendimiento por día de la semana")
public record WeekdayStatsDto(
    @Schema(description = "Día de la semana (MONDAY, TUESDAY, etc.)", example = "SUNDAY")
    DayOfWeek dayOfWeek,

    @Schema(description = "Cantidad total de carreras en este día", example = "42")
    long totalActivities,

    @Schema(description = "Distancia acumulada en kilómetros para este día", example = "450.5")
    double totalDistanceKm,

    @Schema(description = "Ritmo promedio en min/km para este día", example = "4.85")
    double avgPaceMinPerKm,

    @Schema(description = "Frecuencia cardíaca media registrada en este día (bpm)", example = "152")
    double avgHeartRate
) {}
