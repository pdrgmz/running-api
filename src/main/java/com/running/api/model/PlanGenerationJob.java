package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "plan_generation_jobs",
        indexes = {
                @Index(name = "idx_plan_jobs_user_week", columnList = "user_id,week_start"),
                @Index(name = "idx_plan_jobs_status_updated", columnList = "status,updated_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Job de generación de un plan semanal. La API lo marca COMPLETED en la misma transacción que guarda el plan")
public class PlanGenerationJob {

    /** El id es también el requestId que viaja en el JWT y en el webhook de n8n. */
    @Id
    @Schema(description = "requestId único del job", example = "8f3c2a1e-...")
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrainingJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TrainingJobSource source = TrainingJobSource.MANUAL;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "plan_id")
    private String planId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
