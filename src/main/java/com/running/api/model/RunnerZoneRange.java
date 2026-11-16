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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rango de una zona del perfil del corredor.
 *
 * <p>En las zonas de FC {@code minValue}/{@code maxValue} son bpm. En las de ritmo son
 * segundos por kilómetro, con el valor menor como límite rápido y el mayor como límite lento.
 */
@Entity
@Table(name = "runner_zone_ranges",
        indexes = @Index(name = "idx_runner_zone_ranges_profile", columnList = "profile_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Rango de una zona de frecuencia cardíaca o de ritmo")
public class RunnerZoneRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private RunnerZoneProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 16)
    @Schema(description = "Tipo de zona", example = "HEART_RATE")
    private ZoneRangeType zoneType;

    @Column(name = "zone_number", nullable = false)
    @Schema(description = "Número de zona (1-5)", example = "1")
    private Integer zoneNumber;

    @Column(name = "min_value", nullable = false)
    @Schema(description = "Límite inferior: bpm en FC, segundos/km en ritmo", example = "114.0")
    private Double minValue;

    @Column(name = "max_value", nullable = false)
    @Schema(description = "Límite superior: bpm en FC, segundos/km en ritmo", example = "133.0")
    private Double maxValue;
}
