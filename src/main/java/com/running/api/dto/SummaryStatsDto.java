package com.running.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryStatsDto {
    private Long totalActivities;
    private Double totalDistanceMeters;
    private Double totalDistanceKm;
    private Double totalTimeSeconds;
    private String totalTimeFormatted;
    private Double totalElevationGain;
    private Integer totalCalories;
    private Double avgDistanceKm;
    private Integer maxHeartRateGlobal;
}
