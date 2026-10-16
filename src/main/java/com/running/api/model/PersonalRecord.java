package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "personal_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_personal_records_user_category", columnNames = {"user_id", "distance_category"}),
        indexes = @Index(name = "idx_personal_records_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Marca personal (PB) de un usuario para una categoría de distancia")
public class PersonalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID interno de la marca", example = "1")
    private Long id;

    @Column(name = "user_id")
    @Schema(description = "ID del usuario propietario", example = "1")
    private Long userId;

    @Column(name = "distance_category", nullable = false)
    @Schema(description = "Categoría de distancia (1k, 5k, 10k, 15k, 21k, 42k)", example = "10k")
    private String distanceCategory;

    @Column(name = "target_distance_meters", nullable = false)
    @Schema(description = "Distancia objetivo en metros", example = "10000.0")
    private Double targetDistanceMeters;

    @Column(name = "best_time_seconds", nullable = false)
    @Schema(description = "Mejor tiempo en segundos", example = "2712.0")
    private Double bestTimeSeconds;

    @Column(name = "activity_id", nullable = false)
    @Schema(description = "ID de la actividad donde se logró la marca", example = "2026-09-05T07:00:00Z")
    private String activityId;

    @Column(name = "activity_date")
    @Schema(description = "Fecha de la actividad donde se logró la marca", example = "2026-09-05T07:00:00")
    private LocalDateTime activityDate;

    @Column(name = "avg_speed")
    @Schema(description = "Velocidad media en m/s", example = "3.69")
    private Double avgSpeed;

    @Column(name = "avg_pace_min_per_km")
    @Schema(description = "Ritmo medio en min/km", example = "4.52")
    private Double avgPaceMinPerKm;
}
