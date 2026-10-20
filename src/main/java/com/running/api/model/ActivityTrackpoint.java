package com.running.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_trackpoints", indexes = {
    @Index(name = "idx_trackpoint_activity", columnList = "activity_id"),
    @Index(name = "idx_trackpoint_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Punto de telemetría grabado segundo a segundo")
public class ActivityTrackpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID autoincremental del punto", example = "1024")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    @JsonIgnore
    private Activity activity;

    @Schema(description = "Marca temporal del punto", example = "2026-09-05T07:00:05")
    private LocalDateTime timestamp;

    @Schema(description = "Latitud GPS en grados decimales", example = "40.4168")
    private Double latitude;

    @Schema(description = "Longitud GPS en grados decimales", example = "-3.7038")
    private Double longitude;

    @Schema(description = "Altitud en metros", example = "650.0")
    private Double altitudeMeters;

    /**
     * Indica que la altitud fue calculada por el completado (interpolación o modelo digital).
     * Permite distinguir un 0 real (nivel del mar) de un 0 usado como valor ausente en el
     * archivo original.
     */
    @JsonIgnore
    @Schema(hidden = true)
    private Boolean elevationCalculated;

    @Schema(description = "Distancia acumulada desde el inicio en metros", example = "1250.5")
    private Double distanceMeters;

    @Schema(description = "Frecuencia cardíaca en bpm", example = "148")
    private Integer heartRate;

    @Schema(description = "Cadencia en pasos por minuto (spm)", example = "172")
    private Integer cadence;

    @Schema(description = "Velocidad instantánea en m/s", example = "3.42")
    private Double speed;
}
