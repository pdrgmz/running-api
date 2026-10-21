package com.running.api.service;

import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElevationBackfillWorkerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 8, 0, 0);

    @Test
    void onlyQueriesTrackpointsWithoutAltitude() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0);
        ActivityTrackpoint missing = point(40.001, -3.001, null);
        ActivityTrackpoint zero = point(40.002, -3.002, 0.0);
        ActivityTrackpoint last = point(40.003, -3.003, 120.0);
        Activity activity = activity(first, missing, zero, last);

        FakeElevationProvider provider = new FakeElevationProvider(110.0, 115.0);
        ElevationBackfillWorker worker = worker(activity, provider);
        Set<Long> affectedUsers = new HashSet<>();

        int used = worker.processActivity(activity.getId(), 10, 0.0, affectedUsers);

        assertThat(used).isEqualTo(2);
        assertThat(provider.lastCoordinates).hasSize(2);
        assertThat(provider.lastCoordinates.get(0).latitude()).isEqualTo(40.001);
        assertThat(provider.lastCoordinates.get(1).latitude()).isEqualTo(40.002);
        assertThat(missing.getAltitudeMeters()).isEqualTo(110.0);
        assertThat(missing.getElevationCalculated()).isTrue();
        assertThat(zero.getAltitudeMeters()).isEqualTo(115.0);
        assertThat(zero.getElevationCalculated()).isTrue();
        assertThat(activity.getElevationGain()).isEqualTo(20.0);
        assertThat(activity.getElevationLoss()).isEqualTo(0.0);
        assertThat(affectedUsers).containsExactly(7L);
    }

    @Test
    void respectsCallBudgetAndDoesNotExtrapolateTheTail() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0);
        ActivityTrackpoint second = point(40.001, -3.001, null);
        ActivityTrackpoint third = point(40.002, -3.002, null);
        ActivityTrackpoint fourth = point(40.003, -3.003, null);
        ActivityTrackpoint fifth = point(40.004, -3.004, null);
        Activity activity = activity(first, second, third, fourth, fifth);

        FakeElevationProvider provider = new FakeElevationProvider(110.0, 120.0);
        ElevationBackfillWorker worker = worker(activity, provider);

        int used = worker.processActivity(activity.getId(), 2, 0.0, new HashSet<>());

        assertThat(used).isEqualTo(2);
        assertThat(second.getAltitudeMeters()).isEqualTo(110.0);
        assertThat(third.getAltitudeMeters()).isEqualTo(120.0);
        assertThat(fourth.getAltitudeMeters()).isNull();
        assertThat(fifth.getAltitudeMeters()).isNull();
    }

    @Test
    void doesNotFillGapToALaterAltitudeBeyondTheBudget() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0);
        ActivityTrackpoint second = point(40.001, -3.001, null);
        ActivityTrackpoint third = point(40.002, -3.002, null);
        ActivityTrackpoint fourth = point(40.003, -3.003, 200.0);
        Activity activity = activity(first, second, third, fourth);

        FakeElevationProvider provider = new FakeElevationProvider(110.0);
        ElevationBackfillWorker worker = worker(activity, provider);

        int used = worker.processActivity(activity.getId(), 1, 0.0, new HashSet<>());

        assertThat(used).isEqualTo(1);
        assertThat(second.getAltitudeMeters()).isEqualTo(110.0);
        assertThat(third.getAltitudeMeters()).isNull();
        assertThat(fourth.getAltitudeMeters()).isEqualTo(200.0);
    }

    @Test
    void doesNotQueryCalculatedZeroAltitudesAgain() {
        ActivityTrackpoint first = point(40.000, -3.000, 0.0);
        ActivityTrackpoint second = point(40.001, -3.001, 0.0);
        Activity activity = activity(first, second);

        FakeElevationProvider provider = new FakeElevationProvider(0.0, 0.0);
        ElevationBackfillWorker worker = worker(activity, provider);

        int used = worker.processActivity(activity.getId(), 10, 0.0, new HashSet<>());

        assertThat(used).isEqualTo(2);
        assertThat(first.getAltitudeMeters()).isZero();
        assertThat(second.getAltitudeMeters()).isZero();
        assertThat(first.getElevationCalculated()).isTrue();
        assertThat(second.getElevationCalculated()).isTrue();

        int usedOnSecondRun = worker.processActivity(activity.getId(), 10, 0.0, new HashSet<>());

        assertThat(usedOnSecondRun).isZero();
        assertThat(provider.lastCoordinates).hasSize(2);
    }

    @Test
    void samplesByDistanceInsteadOfEveryTrackpoint() {
        ActivityTrackpoint first = point(40.0000, -3.0000, null);
        ActivityTrackpoint close = point(40.0005, -3.0005, null);
        ActivityTrackpoint far = point(40.0010, -3.0010, null);
        ActivityTrackpoint farther = point(40.0020, -3.0020, null);
        Activity activity = activity(first, close, far, farther);

        FakeElevationProvider provider = new FakeElevationProvider(100.0, 200.0, 300.0);
        ElevationBackfillWorker worker = worker(activity, provider);

        int used = worker.processActivity(activity.getId(), 10, 90.0, new HashSet<>());

        assertThat(used).isEqualTo(3);
        assertThat(provider.lastCoordinates).hasSize(3);
        assertThat(first.getAltitudeMeters()).isEqualTo(100.0);
        assertThat(close.getAltitudeMeters()).isEqualTo(150.0);
        assertThat(far.getAltitudeMeters()).isEqualTo(200.0);
        assertThat(farther.getAltitudeMeters()).isEqualTo(300.0);
    }

    @Test
    void recalculatesFlatConstantAltitudes() {
        ActivityTrackpoint[] points = new ActivityTrackpoint[12];
        for (int i = 0; i < points.length; i++) {
            points[i] = point(40.0 + i * 0.001, -3.0, 30.0);
        }
        Activity activity = activity(points);

        FakeElevationProvider provider = new FakeElevationProvider(elevations(12, 100.0));
        ElevationBackfillWorker worker = worker(activity, provider);

        int used = worker.processActivity(activity.getId(), 12, 0.0, new HashSet<>());

        assertThat(used).isEqualTo(12);
        for (int i = 0; i < points.length; i++) {
            assertThat(points[i].getAltitudeMeters()).isEqualTo(100.0 + i);
            assertThat(points[i].getElevationCalculated()).isTrue();
        }
        assertThat(activity.getElevationGain()).isEqualTo(11.0);
        assertThat(activity.getElevationLoss()).isZero();
    }

    @Test
    void keepsVariedAltitudesWhenRecalculatingAFlatActivity() {
        List<ActivityTrackpoint> points = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            points.add(point(40.0 + i * 0.001, -3.0, 30.0));
        }
        ActivityTrackpoint varied = point(40.009, -3.0, 45.0);
        points.add(varied);
        for (int i = 10; i < 20; i++) {
            points.add(point(40.0 + i * 0.001, -3.0, 30.0));
        }
        Activity activity = activity(points.toArray(new ActivityTrackpoint[0]));

        // 19 de 20 puntos con altitud del dispositivo comparten el valor 30.0 (95%).
        FakeElevationProvider provider = new FakeElevationProvider(elevations(19, 100.0));
        ElevationBackfillWorker worker = worker(activity, provider);

        int used = worker.processActivity(activity.getId(), 20, 0.0, new HashSet<>());

        assertThat(used).isEqualTo(19);
        assertThat(provider.lastCoordinates).hasSize(19);
        assertThat(varied.getAltitudeMeters()).isEqualTo(45.0);
        assertThat(varied.getElevationCalculated()).isNull();
        assertThat(points.get(0).getAltitudeMeters()).isEqualTo(100.0);
        assertThat(points.get(19).getAltitudeMeters()).isEqualTo(118.0);
    }

    @Test
    void respectsTheBudgetWhenRecalculatingFlatAltitudes() {
        ActivityTrackpoint[] points = new ActivityTrackpoint[12];
        for (int i = 0; i < points.length; i++) {
            points[i] = point(40.0 + i * 0.001, -3.0, 30.0);
        }
        Activity activity = activity(points);

        FakeElevationProvider provider = new FakeElevationProvider(elevations(5, 100.0));
        ElevationBackfillWorker worker = worker(activity, provider);

        int used = worker.processActivity(activity.getId(), 5, 0.0, new HashSet<>());

        assertThat(used).isEqualTo(5);
        assertThat(points[4].getAltitudeMeters()).isEqualTo(104.0);
        assertThat(points[5].getAltitudeMeters()).isEqualTo(30.0);
    }

    @Test
    void doesNotQueryCalculatedFlatAltitudesAgain() {
        ActivityTrackpoint first = point(40.000, -3.000, 30.0);
        ActivityTrackpoint second = point(40.001, -3.001, 30.0);
        first.setElevationCalculated(true);
        second.setElevationCalculated(true);
        Activity activity = activity(first, second);

        FakeElevationProvider provider = new FakeElevationProvider(elevations(2, 100.0));
        ElevationBackfillWorker worker = worker(activity, provider);

        int used = worker.processActivity(activity.getId(), 10, 0.0, new HashSet<>());

        assertThat(used).isZero();
        assertThat(provider.lastCoordinates).isEmpty();
    }

    private static ElevationBackfillWorker worker(Activity activity, ElevationProvider provider) {
        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findById(activity.getId())).thenReturn(Optional.of(activity));
        return new ElevationBackfillWorker(repository, provider, 0.1);
    }

    private static Activity activity(ActivityTrackpoint... points) {
        Activity activity = Activity.builder()
                .id("activity-1")
                .userId(7L)
                .trackpoints(new ArrayList<>())
                .build();
        for (ActivityTrackpoint point : points) {
            activity.addTrackpoint(point);
        }
        return activity;
    }

    private static ActivityTrackpoint point(double latitude, double longitude, Double altitude) {
        return ActivityTrackpoint.builder()
                .timestamp(START)
                .latitude(latitude)
                .longitude(longitude)
                .altitudeMeters(altitude)
                .build();
    }

    private static Double[] elevations(int count, double start) {
        Double[] values = new Double[count];
        for (int i = 0; i < count; i++) {
            values[i] = start + i;
        }
        return values;
    }

    private static class FakeElevationProvider implements ElevationProvider {

        private final List<Double> elevations;
        private List<Coordinate> lastCoordinates = List.of();

        private FakeElevationProvider(Double... elevations) {
            this.elevations = List.of(elevations);
        }

        @Override
        public List<Double> lookup(List<Coordinate> coordinates) {
            lastCoordinates = new ArrayList<>(coordinates);
            return elevations.subList(0, Math.min(elevations.size(), coordinates.size()));
        }
    }
}
