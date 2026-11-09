package com.running.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.running.api.model.WorkoutType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Contenido de un plan semanal. Es el JSON que n8n envía en el parámetro planJson de save_weekly_plan")
public record WeeklyPlanPayload(
        @Schema(description = "Resumen del plan") String summary,
        @Schema(description = "Notas del entrenador/IA") String coachNotes,
        @Schema(description = "Distancia objetivo semanal en metros") Double targetWeeklyDistanceMeters,
        @Schema(description = "Carga objetivo semanal") Double targetWeeklyLoad,
        @Schema(description = "Sesiones de la semana") List<PlannedWorkoutPayload> workouts,
        @Schema(description = "Modelo de IA usado") String model,
        @Schema(description = "Versión del prompt usado") String promptVersion
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlannedWorkoutPayload(
            @JsonAlias("workoutDate")
            @Schema(description = "Fecha de la sesión", example = "2026-11-16") LocalDate date,
            @Schema(description = "Tipo de sesión", example = "EASY") WorkoutType type,
            @Schema(description = "Título de la sesión", example = "Rodaje suave") String title,
            @Schema(description = "Descripción detallada") String description,
            @JsonAlias("distanceMeters")
            @Schema(description = "Distancia objetivo en metros", example = "8000.0") Double targetDistanceMeters,
            @JsonAlias("durationSeconds")
            @Schema(description = "Duración objetivo en segundos", example = "2880.0") Double targetDurationSeconds,
            @JsonAlias("paceMinPerKm")
            @Schema(description = "Ritmo objetivo en min/km", example = "5.4") Double targetPaceMinPerKm,
            @JsonAlias("hrZone")
            @Schema(description = "Zona de FC objetivo (1-5)", example = "2") Integer targetHrZone,
            @JsonAlias("load")
            @Schema(description = "Carga objetivo de la sesión", example = "55.0") Double targetLoad,
            @Schema(description = "Bloque principal (series, repeticiones, recuperaciones)") String mainSet
    ) {
    }
}
