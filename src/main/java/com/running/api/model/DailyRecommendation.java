package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Recomendación de entrenamiento para un día concreto, generada por la IA a
 * partir de las actividades y el estado del atleta.
 * <p>
 * Solo puede existir una recomendación por usuario y fecha. Al regenerar el
 * mismo día se sobrescribe la fila, conservando el histórico de días
 * anteriores.
 */
@Entity
@Table(name = "daily_recommendations",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_recommendations_user_date",
                columnNames = {"user_id", "recommendation_date"}),
        indexes = {
                @Index(name = "idx_daily_recommendations_user_date",
                        columnList = "user_id,recommendation_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Recomendación diaria de entrenamiento generada por IA y validada por la API")
public class DailyRecommendation {

    @Id
    @Schema(description = "ID único de la recomendación", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String id;

    @Column(name = "user_id", nullable = false)
    @Schema(description = "ID del usuario propietario (resuelto desde el JWT)")
    private Long userId;

    @Column(name = "recommendation_date", nullable = false)
    @Schema(description = "Día al que se refiere la recomendación", example = "2026-11-11")
    private LocalDate recommendationDate;

    @Column(name = "request_id", nullable = false)
    @Schema(description = "requestId del job de generación que produjo la recomendación")
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "Tipo de sesión recomendada", example = "EASY")
    private WorkoutType type;

    @Column(nullable = false)
    @Schema(description = "Título de la sesión", example = "Rodaje suave en Z2")
    private String title;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Descripción detallada de la sesión")
    private String description;

    @Column(name = "main_set", columnDefinition = "TEXT")
    @Schema(description = "Bloque principal (series, repeticiones, recuperaciones)")
    private String mainSet;

    @Column(name = "target_distance_meters")
    @Schema(description = "Distancia objetivo en metros", example = "8000.0")
    private Double targetDistanceMeters;

    @Column(name = "target_duration_seconds")
    @Schema(description = "Duración objetivo en segundos", example = "2880.0")
    private Double targetDurationSeconds;

    @Column(name = "target_pace_min_per_km")
    @Schema(description = "Ritmo objetivo en min/km", example = "5.4")
    private Double targetPaceMinPerKm;

    @Column(name = "target_hr_zone")
    @Schema(description = "Zona de frecuencia cardíaca objetivo (1-5)", example = "2")
    private Integer targetHrZone;

    @Column(name = "target_load")
    @Schema(description = "Carga objetivo de la sesión", example = "55.0")
    private Double targetLoad;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Resumen en una línea de la recomendación")
    private String summary;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Análisis de las actividades y del estado del atleta que justifica la sesión")
    private String analysis;

    @Column(name = "coach_notes", columnDefinition = "TEXT")
    @Schema(description = "Consejos del entrenador/IA para ejecutar la sesión")
    private String coachNotes;

    @Column(name = "model")
    @Schema(description = "Modelo de IA usado", example = "gpt-4.1-mini")
    private String model;

    @Column(name = "prompt_version")
    @Schema(description = "Versión del prompt usado", example = "daily-recommendation-v1")
    private String promptVersion;

    @Column(name = "generated_at")
    @Schema(description = "Momento en que se generó la recomendación")
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (generatedAt == null) {
            generatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
