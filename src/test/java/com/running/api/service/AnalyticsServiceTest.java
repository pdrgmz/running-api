package com.running.api.service;

import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Activity sampleActivity;

    @BeforeEach
    void setUp() {
        sampleActivity = Activity.builder()
                .id("2026-10-07T10:00:00Z")
                .name("Carrera Test")
                .startTime(LocalDateTime.of(2026, 10, 7, 10, 0))
                .distanceMeters(5000.0)
                .totalTimeSeconds(1500.0) // 25 min -> 5.0 m/s avg speed -> 3.33 min/km
                .avgSpeed(3.3333)
                .maxSpeed(4.5)
                .avgHeartRate(155)
                .maxHeartRate(175)
                .build();

        List<ActivityTrackpoint> trackpoints = new ArrayList<>();
        LocalDateTime baseTime = sampleActivity.getStartTime();

        for (int i = 0; i <= 30; i++) {
            trackpoints.add(ActivityTrackpoint.builder()
                    .timestamp(baseTime.plusSeconds(i * 50))
                    .distanceMeters(i * 166.66)
                    .speed(3.33)
                    .heartRate(140 + (i % 30))
                    .cadence(160 + (i % 20))
                    .build());
        }

        sampleActivity.setTrackpoints(trackpoints);
    }

    @Test
    void testCalculateTrainingLoad() {
        when(activityRepository.findById(anyString())).thenReturn(Optional.of(sampleActivity));

        Map<String, Object> result = analyticsService.calculateTrainingLoad("2026-10-07T10:00:00Z");

        assertNotNull(result);
        assertEquals("2026-10-07T10:00:00Z", result.get("activityId"));
        assertTrue((Double) result.get("trainingLoad") > 0.0);
        assertTrue((Double) result.get("edwardsTrimp") > 0.0);
        assertNotNull(result.get("effortLevel"));
    }

    @Test
    void testCalculateVo2MaxAndVam() {
        when(activityRepository.findById(anyString())).thenReturn(Optional.of(sampleActivity));

        Map<String, Object> result = analyticsService.calculateVo2MaxAndVam("2026-10-07T10:00:00Z");

        assertNotNull(result);
        assertEquals("2026-10-07T10:00:00Z", result.get("activityId"));

        Double vo2Max = (Double) result.get("vo2MaxEstimated");
        Double vam = (Double) result.get("vamKmH");

        assertNotNull(vo2Max);
        assertNotNull(vam);
        assertTrue(vo2Max >= 20.0 && vo2Max <= 85.0);
        assertTrue(vam > 0.0);
        assertNotNull(result.get("fitnessCategory"));
        assertNotNull(result.get("predictedRaceTimes"));
    }

    @Test
    void testPopulateMetrics() {
        analyticsService.populateMetrics(sampleActivity);

        assertNotNull(sampleActivity.getAvgCadence());
        assertNotNull(sampleActivity.getMaxCadence());
        assertNotNull(sampleActivity.getAvgPaceMinPerKm());
        assertNotNull(sampleActivity.getMaxPaceMinPerKm());
        assertNotNull(sampleActivity.getTrainingLoad());
        assertNotNull(sampleActivity.getVo2MaxEstimated());
        assertNotNull(sampleActivity.getVamKmH());

        assertEquals(5.0, sampleActivity.getAvgPaceMinPerKm());
        assertTrue(sampleActivity.getAvgCadence() >= 160);
        assertTrue(sampleActivity.getMaxCadence() >= 175);
    }
}
