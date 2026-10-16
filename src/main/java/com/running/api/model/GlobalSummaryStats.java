package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "global_summary_stats", indexes = @Index(name = "idx_global_summary_stats_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Schema(description = "Estadísticas globales acumuladas de un usuario (una fila por usuario)")
public class GlobalSummaryStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID interno de la fila", example = "1")
    private Long id;

    @Column(name = "user_id", unique = true)
    @Schema(description = "ID del usuario propietario", example = "1")
    private Long userId;

    @Column(name = "total_activities", nullable = false)
    @Builder.Default
    @Schema(description = "Número total de actividades", example = "128")
    private Long totalActivities = 0L;

    @Column(name = "total_distance_meters", nullable = false)
    @Builder.Default
    @Schema(description = "Distancia total acumulada en metros", example = "1532000.0")
    private Double totalDistanceMeters = 0.0;

    @Column(name = "total_time_seconds", nullable = false)
    @Builder.Default
    @Schema(description = "Tiempo total acumulado en segundos", example = "486000.0")
    private Double totalTimeSeconds = 0.0;

    @Column(name = "total_elevation_gain", nullable = false)
    @Builder.Default
    @Schema(description = "Desnivel positivo total acumulado en metros", example = "12450.0")
    private Double totalElevationGain = 0.0;

    @Column(name = "total_calories", nullable = false)
    @Builder.Default
    @Schema(description = "Calorías totales acumuladas", example = "98000")
    private Long totalCalories = 0L;

    @Column(name = "max_heart_rate_global", nullable = false)
    @Builder.Default
    @Schema(description = "Frecuencia cardíaca máxima histórica (bpm)", example = "192")
    private Integer maxHeartRateGlobal = 0;

    /**
     * Constructor usado por las proyecciones JPQL de agregados por usuario
     * (no incluye {@code id} ni {@code userId}, que se asignan después).
     */
    public GlobalSummaryStats(Long totalActivities, Double totalDistanceMeters, Double totalTimeSeconds,
                              Double totalElevationGain, Long totalCalories, Integer maxHeartRateGlobal) {
        this.totalActivities = totalActivities;
        this.totalDistanceMeters = totalDistanceMeters;
        this.totalTimeSeconds = totalTimeSeconds;
        this.totalElevationGain = totalElevationGain;
        this.totalCalories = totalCalories;
        this.maxHeartRateGlobal = maxHeartRateGlobal;
    }
}
