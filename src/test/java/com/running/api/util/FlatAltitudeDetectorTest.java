package com.running.api.util;

import com.running.api.model.ActivityTrackpoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlatAltitudeDetectorTest {

    @Test
    void marksTheDominantAltitudeWhenItCoversAtLeastNinetyPercent() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            points.add(point(30.5, null));
        }
        points.add(point(31.0, null));
        points.add(point(32.0, null));

        boolean[] flat = FlatAltitudeDetector.detect(points);

        assertThat(count(flat)).isEqualTo(18);
        assertThat(flat[0]).isTrue();
        assertThat(flat[17]).isTrue();
        assertThat(flat[18]).isFalse();
        assertThat(flat[19]).isFalse();
    }

    @Test
    void ignoresValuesJustBelowTheThreshold() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            points.add(point(30.5, null));
        }
        points.add(point(31.0, null));
        points.add(point(32.0, null));
        points.add(point(33.0, null));

        assertThat(count(FlatAltitudeDetector.detect(points))).isZero();
    }

    @Test
    void ignoresActivitiesWithFewerPointsThanTheMinimum() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            points.add(point(30.5, null));
        }

        assertThat(count(FlatAltitudeDetector.detect(points))).isZero();
    }

    @Test
    void requiresExactlyTheSameAltitudeValue() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            points.add(point(30.5, null));
        }
        for (int i = 0; i < 10; i++) {
            points.add(point(30.5000001, null));
        }

        assertThat(count(FlatAltitudeDetector.detect(points))).isZero();
    }

    @Test
    void ignoresCalculatedAltitudes() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            points.add(point(30.5, true));
        }

        assertThat(count(FlatAltitudeDetector.detect(points))).isZero();
    }

    @Test
    void ignoresNullAndZeroAltitudesInTheRatio() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            points.add(point(30.5, null));
        }
        for (int i = 0; i < 5; i++) {
            points.add(point(null, null));
        }
        for (int i = 0; i < 3; i++) {
            points.add(point(0.0, null));
        }

        boolean[] flat = FlatAltitudeDetector.detect(points);

        assertThat(count(flat)).isEqualTo(12);
        assertThat(flat[12]).isFalse();
        assertThat(flat[16]).isFalse();
    }

    @Test
    void returnsEmptyResultForNullAndEmptyLists() {
        assertThat(FlatAltitudeDetector.detect(null)).isEmpty();
        assertThat(FlatAltitudeDetector.detect(List.of())).isEmpty();
    }

    private static long count(boolean[] values) {
        long count = 0;
        for (boolean value : values) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    private static ActivityTrackpoint point(Double altitude, Boolean calculated) {
        return ActivityTrackpoint.builder()
                .altitudeMeters(altitude)
                .elevationCalculated(calculated)
                .build();
    }
}
