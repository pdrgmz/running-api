package com.running.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "personal_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "distance_category", nullable = false, unique = true)
    private String distanceCategory;

    @Column(name = "target_distance_meters", nullable = false)
    private Double targetDistanceMeters;

    @Column(name = "best_time_seconds", nullable = false)
    private Double bestTimeSeconds;

    @Column(name = "activity_id", nullable = false)
    private String activityId;

    @Column(name = "activity_date")
    private LocalDateTime activityDate;

    @Column(name = "avg_speed")
    private Double avgSpeed;

    @Column(name = "avg_pace_min_per_km")
    private Double avgPaceMinPerKm;
}
