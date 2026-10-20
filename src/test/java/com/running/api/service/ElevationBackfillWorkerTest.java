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

    private static ElevationBackfillWorker worker(Activity activity, ElevationProvider provider) {
        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findById(activity.getId())).thenReturn(Optional.of(activity));
        TrackpointCompletionService completionService =
                new TrackpointCompletionService(provider, 1, 0.1, false);
        return new ElevationBackfillWorker(repository, provider, completionService);
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
