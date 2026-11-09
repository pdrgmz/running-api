package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "training_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_training_preferences_user", columnNames = "user_id"),
        indexes = @Index(name = "idx_training_preferences_enabled", columnList = "enabled"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Preferencias del atleta para la generación automática de planes semanales")
public class TrainingPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    @Schema(description = "ID del usuario propietario")
    private Long userId;

    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "Activa la generación automática semanal", example = "true")
    private Boolean enabled = false;

    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "Zona horaria IANA del atleta", example = "Europe/Madrid")
    private String timezone = "Europe/Madrid";

    @Column(name = "days_per_week")
    @Schema(description = "Número de sesiones que el atleta quiere por semana", example = "5")
    private Integer daysPerWeek;

    @Column(name = "preferred_long_run_day")
    @Schema(description = "Día preferido para el rodaje largo", example = "SATURDAY")
    private String preferredLongRunDay;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Notas o restricciones para el entrenador/IA")
    private String notes;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
