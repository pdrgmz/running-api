package com.running.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;

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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    @JsonIgnore
    private Activity activity;

    private LocalDateTime timestamp;

    private Double latitude;

    private Double longitude;

    private Double altitudeMeters;

    private Double distanceMeters;

    private Integer heartRate;

    private Integer cadence;

    private Double speed;
}
