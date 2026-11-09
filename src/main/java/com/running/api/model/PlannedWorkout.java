package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Table(name = "planned_workouts",
        uniqueConstraints = @UniqueConstraint(name = "uk_planned_workouts_plan_date",
                columnNames = {"plan_id", "workout_date"}),
        indexes = @Index(name = "idx_planned_workouts_plan", columnList = "plan_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "plan")
@Schema(description = "Sesión de entrenamiento dentro de un plan semanal")
public class PlannedWorkout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private TrainingPlan plan;

    @Column(name = "workout_date", nullable = false)
    @Schema(description = "Fecha de la sesión", example = "2026-11-16")
    private LocalDate workoutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "Tipo de sesión", example = "EASY")
    private WorkoutType type;

    @Column(nullable = false)
    @Schema(description = "Título de la sesión", example = "Rodaje suave")
    private String title;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Descripción detallada de la sesión")
    private String description;

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

    @Column(name = "main_set", columnDefinition = "TEXT")
    @Schema(description = "Bloque principal de la sesión (series, repeticiones, recuperaciones)")
    private String mainSet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "Estado de la sesión", example = "PLANNED")
    private WorkoutStatus status = WorkoutStatus.PLANNED;

    @Column(name = "completed_activity_id")
    @Schema(description = "ID de la actividad con la que se completó la sesión, si aplica")
    private String completedActivityId;

    @Column(name = "rpe")
    @Schema(description = "Esfuerzo percibido (1-10), si se registra")
    private Integer rpe;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Feedback del atleta sobre la sesión")
    private String feedback;
}
