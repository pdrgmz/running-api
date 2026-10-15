package com.running.api.mapper;

import com.running.api.dto.FitActivityDTO;
import com.running.api.model.Activity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ActivityMapperTest {

    private final ActivityMapper mapper = new ActivityMapper();

    private FitActivityDTO.TrackPoint trackPoint(int heartRate, float speed, int cadence) {
        FitActivityDTO.TrackPoint point = new FitActivityDTO.TrackPoint();
        point.setHeartRate(heartRate);
        point.setSpeed(speed);
        point.setCadence(cadence);
        return point;
    }

    @Test
    void mapsSummaryAndDerivesMetrics() {
        FitActivityDTO dto = new FitActivityDTO();
        dto.setTimestamp(LocalDateTime.of(2026, 1, 1, 8, 0));
        dto.setTotalDistanceMeters(1000.0);
        dto.setTotalTimerTimeSeconds(300.0);
        dto.getTrackPoints().add(trackPoint(140, 3.0f, 80));
        dto.getTrackPoints().add(trackPoint(160, 5.0f, 84));
        dto.getTrackPoints().add(trackPoint(150, 4.0f, 82));

        Activity activity = mapper.toEntity(dto, "/tmp/x.fit");

        assertThat(activity.getSourceFileType()).isEqualTo("fit");
        assertThat(activity.getBackupFilePath()).isEqualTo("/tmp/x.fit");
        assertThat(activity.getDistanceMeters()).isCloseTo(1000.0, within(1e-9));
        assertThat(activity.getTotalTimeSeconds()).isCloseTo(300.0, within(1e-9));
        assertThat(activity.getMaxSpeed()).isCloseTo(5.0, within(1e-9));
        // Ritmo máximo = (1000 / 5) / 60 = 3.333 -> 3.33 min/km
        assertThat(activity.getMaxPaceMinPerKm()).isCloseTo(3.33, within(0.001));
        // Velocidad media = 1000 / 300 m/s
        assertThat(activity.getAvgSpeed()).isCloseTo(3.3333, within(1e-4));
        // Ritmo medio = (1000 / 3.3333) / 60 = 5.0 min/km
        assertThat(activity.getAvgPaceMinPerKm()).isCloseTo(5.0, within(0.01));
        assertThat(activity.getAvgHeartRate()).isEqualTo(150);
        assertThat(activity.getMaxHeartRate()).isEqualTo(160);
        assertThat(activity.getAvgCadence()).isEqualTo(82);
        assertThat(activity.getMaxCadence()).isEqualTo(84);
        assertThat(activity.getTrackpoints()).hasSize(3);
    }

    @Test
    void returnsNullForNullDto() {
        assertThat(mapper.toEntity(null, "")).isNull();
    }
}
