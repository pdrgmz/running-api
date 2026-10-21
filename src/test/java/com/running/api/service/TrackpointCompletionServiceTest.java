package com.running.api.service;

import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrackpointCompletionServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 5, 7, 0);

    private final TrackpointCompletionService service = new TrackpointCompletionService(0.1);

    @Test
    void fillsTelemetryGapsBetweenNeighbors() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        points.add(point(0, 40.000, -3.000, 100.0, 0.0, 140, 160, 2.0));
        points.add(point(1, null, null, null, 10.0, null, 160, null));
        points.add(point(2, 40.002, -3.002, null, 20.0, null, 160, null));
        points.add(point(3, 40.003, -3.003, 130.0, 30.0, 170, 160, 4.0));
        points.add(point(4, 40.004, -3.004, 130.0, 40.0, 150, 160, 4.0));
        Activity activity = activity(points);

        service.completeAfterImport(activity);

        assertThat(points.get(1).getLatitude()).isCloseTo(40.001, within(1e-9));
        assertThat(points.get(1).getLongitude()).isCloseTo(-3.001, within(1e-9));
        assertThat(points.get(1).getAltitudeMeters()).isEqualTo(110.0);
        assertThat(points.get(2).getAltitudeMeters()).isEqualTo(120.0);
        assertThat(points.get(1).getElevationCalculated()).isTrue();
        assertThat(points.get(2).getElevationCalculated()).isTrue();
        assertThat(points.get(0).getElevationCalculated()).isNull();
        assertThat(points.get(1).getHeartRate()).isEqualTo(150);
        assertThat(points.get(2).getHeartRate()).isEqualTo(160);
        assertThat(points.get(1).getSpeed()).isCloseTo(2.667, within(1e-9));
        assertThat(points.get(2).getSpeed()).isCloseTo(3.333, within(1e-9));

        assertThat(activity.getAvgHeartRate()).isEqualTo(154);
        assertThat(activity.getMaxHeartRate()).isEqualTo(170);
        assertThat(activity.getAvgCadence()).isEqualTo(160);
        assertThat(activity.getMaxCadence()).isEqualTo(160);
        assertThat(activity.getMaxSpeed()).isEqualTo(4.0);
        assertThat(activity.getElevationGain()).isEqualTo(30.0);
        assertThat(activity.getElevationLoss()).isZero();
    }

    @Test
    void discardsImplausibleReadingsBeforeInterpolating() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        points.add(point(0, 40.000, -3.000, 100.0, 0.0, 140, 160, 2.0));
        points.add(point(1, 40.001, -3.001, 100.0, 3.0, 0, 500, 50.0));
        points.add(point(2, 40.002, -3.002, 100.0, 6.0, 170, 175, 4.0));
        Activity activity = activity(points);

        service.completeAfterImport(activity);

        assertThat(points.get(1).getHeartRate()).isEqualTo(155);
        assertThat(points.get(1).getCadence()).isEqualTo(168);
        assertThat(points.get(1).getSpeed()).isEqualTo(3.0);
    }

    @Test
    void discardsDistanceGlitchesAndKeepsTheSeriesMonotonic() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        points.add(point(0, 40.000, -3.000, 100.0, 0.0, 140, 160, null));
        points.add(point(1, 40.001, -3.001, 100.0, 1000.0, 140, 160, null));
        points.add(point(2, 40.002, -3.002, 100.0, 1010.0, 140, 160, null));
        Activity activity = activity(points);

        service.completeAfterImport(activity);

        assertThat(points.get(0).getDistanceMeters()).isZero();
        assertThat(points.get(1).getDistanceMeters()).isZero();
        assertThat(points.get(2).getDistanceMeters()).isEqualTo(10.0);
    }

    @Test
    void derivesSpeedFromDistanceWhenNoSpeedIsPresent() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            points.add(point(i, 40.0 + i * 0.001, -3.0, 100.0, i * 3.0, 140, 160, null));
        }
        Activity activity = activity(points);

        service.completeAfterImport(activity);

        for (ActivityTrackpoint point : points) {
            assertThat(point.getSpeed()).isEqualTo(3.0);
        }
    }

    @Test
    void keepsValidValuesUntouched() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        points.add(point(0, 40.000, -3.000, 100.0, 0.0, 140, 160, 2.5));
        points.add(point(1, 40.001, -3.001, 110.0, 10.0, 150, 170, 3.0));
        Activity activity = activity(points);

        service.completeAfterImport(activity);

        assertThat(points.get(0).getLatitude()).isEqualTo(40.000);
        assertThat(points.get(1).getLongitude()).isEqualTo(-3.001);
        assertThat(points.get(0).getAltitudeMeters()).isEqualTo(100.0);
        assertThat(points.get(1).getAltitudeMeters()).isEqualTo(110.0);
        assertThat(points.get(0).getElevationCalculated()).isNull();
        assertThat(points.get(0).getHeartRate()).isEqualTo(140);
        assertThat(points.get(1).getCadence()).isEqualTo(170);
        assertThat(points.get(0).getSpeed()).isEqualTo(2.5);
        assertThat(points.get(1).getSpeed()).isEqualTo(3.0);
        assertThat(points.get(0).getDistanceMeters()).isZero();
        assertThat(points.get(1).getDistanceMeters()).isEqualTo(10.0);
        assertThat(activity.getElevationGain()).isEqualTo(10.0);
    }

    @Test
    void leavesAltitudeNullWhenNoPointHasIt() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        points.add(point(0, 40.000, -3.000, null, 0.0, 140, 160, 2.5));
        points.add(point(1, 40.001, -3.001, null, 10.0, 150, 170, 3.0));
        Activity activity = activity(points);

        service.completeAfterImport(activity);

        assertThat(points.get(0).getAltitudeMeters()).isNull();
        assertThat(points.get(1).getAltitudeMeters()).isNull();
        assertThat(points.get(0).getElevationCalculated()).isNull();
        assertThat(activity.getElevationGain()).isNull();
        assertThat(activity.getElevationLoss()).isNull();
    }

    @Test
    void ignoresActivitiesWithoutTrackpoints() {
        Activity activity = activity(new ArrayList<>());

        service.completeAfterImport(activity);
        service.completeAfterImport(null);

        assertThat(activity.getTrackpoints()).isEmpty();
    }

    private static Activity activity(List<ActivityTrackpoint> points) {
        Activity activity = new Activity();
        activity.setId("2026-09-05T07:00:00Z");
        activity.setUserId(1L);
        activity.setTrackpoints(points);
        return activity;
    }

    private static ActivityTrackpoint point(int second, Double latitude, Double longitude,
                                            Double altitude, Double distance, Integer heartRate,
                                            Integer cadence, Double speed) {
        ActivityTrackpoint point = new ActivityTrackpoint();
        point.setTimestamp(START.plusSeconds(second));
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        point.setAltitudeMeters(altitude);
        point.setDistanceMeters(distance);
        point.setHeartRate(heartRate);
        point.setCadence(cadence);
        point.setSpeed(speed);
        return point;
    }
}
