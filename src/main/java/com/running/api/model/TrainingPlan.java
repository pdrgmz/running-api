package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "training_plans",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_training_plans_request_id", columnNames = "request_id"),
                @UniqueConstraint(name = "uk_training_plans_user_week_version",
                        columnNames = {"user_id", "week_start", "version"})
        },
        indexes = {
                @Index(name = "idx_training_plans_user_week", columnList = "user_id,week_start"),
                @Index(name = "idx_training_plans_user_status", columnList = "user_id,status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "workouts")
@Schema(description = "Plan de entrenamiento semanal generado por IA y validado por la API")
public class TrainingPlan {

    @Id
    @Schema(description = "ID único del plan", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String id;

    @Column(name = "user_id", nullable = false)
    @Schema(description = "ID del usuario propietario (resuelto desde el JWT)")
    private Long userId;

    @Column(name = "week_start", nullable = false)
    @Schema(description = "Lunes de la semana planificada", example = "2026-11-16")
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    @Schema(description = "Domingo de la semana planificada", example = "2026-11-22")
    private LocalDate weekEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "Estado del plan", example = "ACTIVE")
    private PlanStatus status;

    @Column(nullable = false)
    @Schema(description = "Versión del plan para la misma semana", example = "1")
    private Integer version;

    @Column(name = "request_id", nullable = false)
    @Schema(description = "requestId del job de generación que produjo este plan")
    private String requestId;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Resumen del plan")
    private String summary;

    @Column(name = "coach_notes", columnDefinition = "TEXT")
    @Schema(description = "Notas del entrenador/IA")
    private String coachNotes;

    @Column(name = "target_weekly_distance_meters")
    @Schema(description = "Distancia objetivo semanal en metros", example = "42000.0")
    private Double targetWeeklyDistanceMeters;

    @Column(name = "target_weekly_load")
    @Schema(description = "Carga objetivo semanal", example = "320.0")
    private Double targetWeeklyLoad;

    @Column(name = "model")
    @Schema(description = "Modelo de IA usado para generar el plan", example = "gpt-5.1")
    private String model;

    @Column(name = "prompt_version")
    @Schema(description = "Versión del prompt usado", example = "weekly-plan-v1")
    private String promptVersion;

    @Column(name = "generated_at")
    @Schema(description = "Momento en que se generó el plan")
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("workoutDate ASC, id ASC")
    @Builder.Default
    private List<PlannedWorkout> workouts = new ArrayList<>();

    public void addWorkout(PlannedWorkout workout) {
        workouts.add(workout);
        workout.setPlan(this);
    }

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
