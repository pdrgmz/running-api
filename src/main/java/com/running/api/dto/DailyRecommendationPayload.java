package com.running.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.running.api.model.WorkoutType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contenido de una recomendación diaria. Es el JSON que n8n envía en el
 * parámetro {@code recommendationJson} de {@code save_daily_recommendation}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Recomendación de entrenamiento para un día concreto")
public record DailyRecommendationPayload(
        @Schema(description = "Resumen en una línea", example = "Rodaje suave de 45 minutos en Z2")
        String summary,
        @Schema(description = "Análisis de las actividades y del estado del atleta que justifica la sesión")
        String analysis,
        @Schema(description = "Consejos para ejecutar la sesión")
        String coachNotes,
        @Schema(description = "Sesión recomendada para el día")
        WorkoutPayload workout,
        @Schema(description = "Versión del prompt usado")
        String promptVersion
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkoutPayload(
            @Schema(description = "Tipo de sesión. REST es válido para días de descanso.", example = "EASY")
            WorkoutType type,
            @Schema(description = "Título de la sesión", example = "Rodaje suave")
            String title,
            @Schema(description = "Descripción detallada")
            String description,
            @JsonAlias("distanceMeters")
            @Schema(description = "Distancia objetivo en metros", example = "8000.0")
            Double targetDistanceMeters,
            @JsonAlias("durationSeconds")
            @Schema(description = "Duración objetivo en segundos", example = "2880.0")
            Double targetDurationSeconds,
            @JsonAlias("paceMinPerKm")
            @Schema(description = "Ritmo objetivo en min/km", example = "5.4")
            Double targetPaceMinPerKm,
            @JsonAlias("hrZone")
            @Schema(description = "Zona de FC objetivo (1-5)", example = "2")
            Integer targetHrZone,
            @JsonAlias("load")
            @Schema(description = "Carga objetivo de la sesión", example = "55.0")
            Double targetLoad,
            @Schema(description = "Bloque principal (series, repeticiones, recuperaciones)")
            String mainSet
    ) {
    }
}
