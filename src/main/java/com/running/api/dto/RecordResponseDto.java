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
public class RecordResponseDto {
    private String distanceCategory;
    private Double targetDistanceMeters;
    private Double bestTimeSeconds;
    private String bestTimeFormatted;
    private String activityId;
    private LocalDateTime activityDate;
    private Double avgSpeed;
    private Double avgPaceMinPerKm;
}
