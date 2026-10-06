package com.running.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Entidad que representa el resumen consolidado de una carrera")
public class Activity {

    @Id
    @Schema(description = "ID único de la actividad (ISO Timestamp o UUID)", example = "2026-09-05T07:00:00Z")
    private String id;

    @Schema(description = "Nombre asignado a la carrera")
    private String name;

    private LocalDateTime startTime;

    @Schema(description = "Distancia total recorrida en metros")
    private Double distanceMeters;

    @Schema(description = "Tiempo total transcurrido en segundos")
    private Double totalTimeSeconds;

    @Schema(description = "Velocidad promedio en m/s")
    private Double avgSpeed;

    @Schema(description = "Velocidad máxima alcanzada en m/s")
    private Double maxSpeed;

    @Schema(description = "Frecuencia cardíaca media (bpm)")
    private Integer avgHeartRate;

    @Schema(description = "Frecuencia cardíaca máxima (bpm)")
    private Integer maxHeartRate;

    private Integer totalCalories;

    @Schema(description = "Desnivel positivo acumulado (D+) en metros")
    private Double elevationGain;

    @Schema(description = "Desnivel negativo acumulado (D-) en metros")
    private Double elevationLoss;

    @Schema(description = "Ruta absoluta del archivo de respaldo .tcx guardado en disco")
    private String backupFilePath;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<ActivityTrackpoint> trackpoints = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void addTrackpoint(ActivityTrackpoint trackpoint) {
        trackpoints.add(trackpoint);
        trackpoint.setActivity(this);
    }
}
