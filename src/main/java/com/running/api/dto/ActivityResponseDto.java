package com.running.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponseDto {
    private String id;
    private String name;
    private LocalDateTime startTime;
    private Double distanceMeters;
    private Double totalTimeSeconds;
    private Double avgSpeed;
    private Double maxSpeed;
    private String avgPaceFormatted;
    private Double avgPaceMinPerKm;
    private Double maxPaceMinPerKm;
    private Integer avgHeartRate;
    private Integer maxHeartRate;
    private Integer avgCadence;
    private Integer maxCadence;
    private Double trainingLoad;
    private Double vo2MaxEstimated;
    private Double vamKmH;
    private Integer totalCalories;
    private Double elevationGain;
    private Double elevationLoss;
    private Integer totalTrackpoints;
    private String backupFilePath;
}
