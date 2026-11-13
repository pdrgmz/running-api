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

import java.time.LocalDateTime;

/**
 * Correlación entre una petición de análisis de actividad, el JWT efímero y el
 * resultado persistido en la actividad. El {@code id} es el {@code requestId}.
 */
@Entity
@Table(name = "activity_analysis_jobs",
        indexes = {
                @Index(name = "idx_activity_analysis_jobs_user", columnList = "user_id"),
                @Index(name = "idx_activity_analysis_jobs_activity", columnList = "activity_id"),
                @Index(name = "idx_activity_analysis_jobs_status_updated", columnList = "status,updated_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Job de análisis de actividad generado por n8n. "
        + "La API lo marca COMPLETED al guardar el análisis en la actividad")
public class ActivityAnalysisJob {

    /** El id es también el requestId que viaja en el JWT y en el webhook de n8n. */
    @Id
    @Schema(description = "requestId único del job", example = "8f3c2a1e-...")
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_id", nullable = false)
    @Schema(description = "ID de la actividad analizada", example = "2026-09-05T07:00:00Z")
    private String activityId;

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

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
