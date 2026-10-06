package com.running.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "global_summary_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class GlobalSummaryStats {

    @Id
    private Long id = 1L;

    @Column(name = "total_activities", nullable = false)
    @Builder.Default
    private Long totalActivities = 0L;

    @Column(name = "total_distance_meters", nullable = false)
    @Builder.Default
    private Double totalDistanceMeters = 0.0;

    @Column(name = "total_time_seconds", nullable = false)
    @Builder.Default
    private Double totalTimeSeconds = 0.0;

    @Column(name = "total_elevation_gain", nullable = false)
    @Builder.Default
    private Double totalElevationGain = 0.0;

    @Column(name = "total_calories", nullable = false)
    @Builder.Default
    private Long totalCalories = 0L;

    @Column(name = "max_heart_rate_global", nullable = false)
    @Builder.Default
    private Integer maxHeartRateGlobal = 0;
}