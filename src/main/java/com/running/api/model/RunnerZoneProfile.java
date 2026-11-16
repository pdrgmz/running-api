package com.running.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Perfil de zonas del corredor: una fila por usuario con sus parámetros fisiológicos y los
 * rangos de las cinco zonas de frecuencia cardíaca y de ritmo.
 *
 * <p>Los rangos se guardan en {@link RunnerZoneRange} para poder distinguir entre un perfil
 * calculado desde el historial y uno editado manualmente.
 */
@Entity
@Table(name = "runner_zone_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_runner_zone_profiles_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Perfil de zonas de frecuencia cardíaca y ritmo del corredor")
public class RunnerZoneProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID interno del perfil", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    @Schema(description = "ID del usuario propietario", example = "1")
    private Long userId;

    @Column(name = "max_heart_rate")
    @Schema(description = "FC máxima de referencia del atleta (bpm)", example = "190")
    private Integer maxHeartRate;

    @Column(name = "resting_heart_rate")
    @Schema(description = "FC de reposo del atleta (bpm)", example = "52")
    private Integer restingHeartRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "heart_rate_method", nullable = false, length = 32)
    @Builder.Default
    @Schema(description = "Método de cálculo de las zonas de FC", example = "MAX_HR_PERCENT")
    private HrZoneMethod heartRateMethod = HrZoneMethod.MAX_HR_PERCENT;

    @Column(name = "threshold_pace_seconds_per_km")
    @Schema(description = "Ritmo umbral de referencia (segundos por kilómetro)", example = "270.0")
    private Double thresholdPaceSecondsPerKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "pace_method", nullable = false, length = 32)
    @Builder.Default
    @Schema(description = "Método de cálculo de las zonas de ritmo", example = "THRESHOLD_PACE_PERCENT")
    private PaceZoneMethod paceMethod = PaceZoneMethod.THRESHOLD_PACE_PERCENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    @Schema(description = "Origen del perfil", example = "CALCULATED")
    private ZoneProfileSource source = ZoneProfileSource.DEFAULT;

    @Column(name = "calculated_at")
    @Schema(description = "Última vez que se calcularon las zonas desde el historial")
    private LocalDateTime calculatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("zoneType ASC, zoneNumber ASC")
    @Builder.Default
    @Schema(description = "Rangos de las zonas de FC y de ritmo")
    private List<RunnerZoneRange> zones = new ArrayList<>();

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
