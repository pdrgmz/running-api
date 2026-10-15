package com.running.api.util;

import com.running.api.model.ActivityTrackpoint;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceSeriesTest {

    private static final double MAX_SPEED = 15.0;

    @Test
    void keepsNormalIncrementsUnchanged() {
        double[] result = DistanceSeries.reconstruct(
                new double[]{0, 100, 200}, new double[]{0, 10, 20}, 3, MAX_SPEED);

        assertThat(result).containsExactly(0.0, 100.0, 200.0);
    }

    @Test
    void dropsImpossibleJump() {
        // +5000 m en 1 s (5000 m/s) es un glitch; los incrementos normales se conservan.
        double[] result = DistanceSeries.reconstruct(
                new double[]{0, 5000, 5010}, new double[]{0, 1, 11}, 3, MAX_SPEED);

        assertThat(result).containsExactly(0.0, 0.0, 10.0);
    }

    @Test
    void treatsCounterResetAsZeroIncrement() {
        double[] result = DistanceSeries.reconstruct(
                new double[]{0, 100, 50, 60}, new double[]{0, 10, 20, 30}, 4, MAX_SPEED);

        assertThat(result).containsExactly(0.0, 100.0, 100.0, 110.0);
    }

    @Test
    void allowsLargeDeltaOverLongGap() {
        // 2000 m en 300 s = 6.67 m/s: por debajo del límite, no se descarta.
        double[] result = DistanceSeries.reconstruct(
                new double[]{0, 2000}, new double[]{0, 300}, 2, MAX_SPEED);

        assertThat(result).containsExactly(0.0, 2000.0);
    }

    @Test
    void reconstructPerPointCarriesForwardMissingSamples() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 8, 0, 0);
        List<ActivityTrackpoint> points = List.of(
                point(0.0, base),
                point(100.0, base.plusSeconds(10)),
                point(null, base.plusSeconds(20)),
                point(200.0, base.plusSeconds(30)));

        double[] result = DistanceSeries.reconstructPerPoint(points, MAX_SPEED);

        assertThat(result).containsExactly(0.0, 100.0, 100.0, 200.0);
    }

    private static ActivityTrackpoint point(Double distance, LocalDateTime timestamp) {
        return ActivityTrackpoint.builder().distanceMeters(distance).timestamp(timestamp).build();
    }
}
