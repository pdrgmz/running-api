package com.running.api.service;

import com.running.api.dto.TrackpointCompletionResultDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrackpointCompletionServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 8, 0, 0);

    private final FakeElevationProvider elevationProvider = new FakeElevationProvider();

    @Test
    void fillsMissingValuesWithNeighborInterpolation() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 3.0);
        ActivityTrackpoint middle = point(null, null, null, null, null, null);
        ActivityTrackpoint last = point(40.002, -3.002, 200.0, 160, 90, 5.0);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointCompletionResultDto result = service.complete(activity);

        assertThat(middle.getLatitude()).isCloseTo(40.001, within(1e-9));
        assertThat(middle.getLongitude()).isCloseTo(-3.001, within(1e-9));
        assertThat(middle.getAltitudeMeters()).isCloseTo(150.0, within(1e-9));
        assertThat(middle.getHeartRate()).isEqualTo(150);
        assertThat(middle.getCadence()).isEqualTo(85);
        assertThat(middle.getSpeed()).isCloseTo(4.0, within(1e-9));

        assertThat(result.getLatitudeFilled()).isEqualTo(1);
        assertThat(result.getLongitudeFilled()).isEqualTo(1);
        assertThat(result.getAltitudeFilled()).isEqualTo(1);
        assertThat(result.getHeartRateFilled()).isEqualTo(1);
        assertThat(result.getCadenceFilled()).isEqualTo(1);
        assertThat(result.getSpeedFilled()).isEqualTo(1);
        assertThat(result.isExternalElevationUsed()).isFalse();
        assertThat(result.getAltitudeSource()).isEqualTo("trackpoints");
        assertThat(elevationProvider.calls).isZero();
    }

    @Test
    void fillsEdgesWithNearestNeighbor() {
        ActivityTrackpoint first = point(null, null, null, null, null, null);
        ActivityTrackpoint middle = point(40.000, -3.000, 100.0, 150, 85, 3.0);
        ActivityTrackpoint last = point(null, null, null, null, null, null);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        service.complete(activity);

        assertThat(first.getLatitude()).isEqualTo(40.000);
        assertThat(first.getLongitude()).isEqualTo(-3.000);
        assertThat(first.getAltitudeMeters()).isEqualTo(100.0);
        assertThat(first.getHeartRate()).isEqualTo(150);
        assertThat(first.getCadence()).isEqualTo(85);
        assertThat(first.getSpeed()).isEqualTo(3.0);

        assertThat(last.getLatitude()).isEqualTo(40.000);
        assertThat(last.getAltitudeMeters()).isEqualTo(100.0);
        assertThat(last.getHeartRate()).isEqualTo(150);
    }

    @Test
    void fillsAltitudeGapWhenSomeAltitudesAreValid() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, null, null, null);
        ActivityTrackpoint middle = point(40.001, -3.001, 0.0, null, null, null);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, null, null, null);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointCompletionResultDto result = service.complete(activity);

        assertThat(middle.getAltitudeMeters()).isCloseTo(110.0, within(1e-9));
        assertThat(middle.getElevationCalculated()).isTrue();
        assertThat(result.getAltitudeFilled()).isEqualTo(1);
        assertThat(result.isExternalElevationUsed()).isFalse();
        assertThat(elevationProvider.calls).isZero();
    }

    @Test
    void treatsCalculatedZeroAsAValidAltitude() {
        ActivityTrackpoint first = point(40.000, -3.000, 0.0, null, null, null);
        first.setElevationCalculated(true);
        ActivityTrackpoint middle = point(40.001, -3.001, null, null, null, null);
        ActivityTrackpoint last = point(40.002, -3.002, 100.0, null, null, null);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 1, 0.1);
        TrackpointCompletionResultDto result = service.complete(activity);

        assertThat(elevationProvider.calls).isZero();
        assertThat(first.getAltitudeMeters()).isZero();
        assertThat(middle.getAltitudeMeters()).isCloseTo(50.0, within(1e-9));
        assertThat(middle.getElevationCalculated()).isTrue();
        assertThat(result.isExternalElevationUsed()).isFalse();
    }

    @Test
    void usesExternalElevationWhenAllAltitudesAreZero() {
        ActivityTrackpoint first = point(40.000, -3.000, 0.0, null, null, null);
        ActivityTrackpoint middle = point(40.001, -3.001, 0.0, null, null, null);
        ActivityTrackpoint last = point(40.002, -3.002, 0.0, null, null, null);
        Activity activity = activity(first, middle, last);

        // sampleEvery=1 para consultar las tres coordenadas al proveedor falso.
        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 1, 0.1);
        TrackpointCompletionResultDto result = service.complete(activity);

        assertThat(first.getAltitudeMeters()).isCloseTo(100.0, within(1e-9));
        assertThat(middle.getAltitudeMeters()).isCloseTo(101.0, within(1e-9));
        assertThat(last.getAltitudeMeters()).isCloseTo(102.0, within(1e-9));
        assertThat(first.getElevationCalculated()).isTrue();
        assertThat(middle.getElevationCalculated()).isTrue();
        assertThat(last.getElevationCalculated()).isTrue();
        assertThat(result.getAltitudeFilled()).isEqualTo(3);
        assertThat(result.isExternalElevationUsed()).isTrue();
        assertThat(result.getAltitudeSource()).isEqualTo("open-meteo");
        assertThat(elevationProvider.calls).isEqualTo(1);
    }

    @Test
    void samplesCoordinatesAndInterpolatesRemainingAltitudes() {
        ActivityTrackpoint first = point(40.000, -3.000, 0.0, null, null, null);
        ActivityTrackpoint second = point(40.001, -3.001, 0.0, null, null, null);
        ActivityTrackpoint third = point(40.002, -3.002, 0.0, null, null, null);
        Activity activity = activity(first, second, third);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 2, 0.1);
        service.complete(activity);

        // Se consultan los puntos 0 y 2; el punto 1 se interpola.
        assertThat(elevationProvider.lastCoordinates).hasSize(2);
        assertThat(second.getAltitudeMeters()).isCloseTo(101.0, within(1e-9));
    }

    @Test
    void doesNotCallElevationProviderWithoutCoordinates() {
        ActivityTrackpoint first = point(null, null, 0.0, null, null, null);
        ActivityTrackpoint second = point(null, null, 0.0, null, null, null);
        Activity activity = activity(first, second);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 1, 0.1);
        TrackpointCompletionResultDto result = service.complete(activity);

        assertThat(elevationProvider.calls).isZero();
        assertThat(first.getAltitudeMeters()).isEqualTo(0.0);
        assertThat(second.getAltitudeMeters()).isEqualTo(0.0);
        assertThat(result.isExternalElevationUsed()).isFalse();
        assertThat(result.getAltitudeSource()).isEqualTo("none");
    }

    @Test
    void derivesSpeedFromDistanceAndTimeWhenNoSpeedIsPresent() {
        ActivityTrackpoint first = point(40.000, -3.000, null, null, null, 0.0);
        first.setTimestamp(START);
        first.setDistanceMeters(0.0);

        ActivityTrackpoint second = point(40.001, -3.001, null, null, null, 0.0);
        second.setTimestamp(START.plusSeconds(5));
        second.setDistanceMeters(10.0);

        ActivityTrackpoint third = point(40.002, -3.002, null, null, null, 0.0);
        third.setTimestamp(START.plusSeconds(10));
        third.setDistanceMeters(20.0);

        Activity activity = activity(first, second, third);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointCompletionResultDto result = service.complete(activity);

        assertThat(first.getSpeed()).isCloseTo(2.0, within(1e-9));
        assertThat(second.getSpeed()).isCloseTo(2.0, within(1e-9));
        assertThat(third.getSpeed()).isCloseTo(2.0, within(1e-9));
        assertThat(result.getSpeedFilled()).isEqualTo(3);
    }

    @Test
    void updatesActivitySummaryFromCompletedTrackpoints() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 3.0);
        ActivityTrackpoint second = point(40.001, -3.001, 110.0, 150, 85, 4.0);
        ActivityTrackpoint third = point(40.002, -3.002, 120.0, 160, 90, 5.0);
        Activity activity = activity(first, second, third);
        activity.setDistanceMeters(1000.0);
        activity.setTotalTimeSeconds(300.0);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        service.complete(activity);

        assertThat(activity.getAvgHeartRate()).isEqualTo(150);
        assertThat(activity.getMaxHeartRate()).isEqualTo(160);
        assertThat(activity.getAvgCadence()).isEqualTo(85);
        assertThat(activity.getMaxCadence()).isEqualTo(90);
        assertThat(activity.getMaxSpeed()).isCloseTo(5.0, within(1e-9));
        assertThat(activity.getElevationGain()).isCloseTo(20.0, within(1e-9));
        assertThat(activity.getElevationLoss()).isCloseTo(0.0, within(1e-9));
        assertThat(activity.getAvgSpeed()).isCloseTo(1000.0 / 300.0, within(1e-9));
    }

    @Test
    void doesNotCallExternalProviderAfterImportWhenDisabled() {
        ActivityTrackpoint first = point(40.000, -3.000, 0.0, null, null, null);
        ActivityTrackpoint second = point(40.001, -3.001, 0.0, null, null, null);
        Activity activity = activity(first, second);

        TrackpointCompletionService service =
                new TrackpointCompletionService(elevationProvider, 1, 0.1, false);
        TrackpointCompletionResultDto result = service.completeAfterImport(activity);

        assertThat(elevationProvider.calls).isZero();
        assertThat(result.isExternalElevationUsed()).isFalse();
        assertThat(first.getAltitudeMeters()).isEqualTo(0.0);
        assertThat(second.getAltitudeMeters()).isEqualTo(0.0);
    }

    private static Activity activity(ActivityTrackpoint... points) {
        Activity activity = Activity.builder()
                .id("activity-1")
                .trackpoints(new ArrayList<>())
                .build();
        for (ActivityTrackpoint point : points) {
            activity.addTrackpoint(point);
        }
        return activity;
    }

    private static ActivityTrackpoint point(Double latitude, Double longitude, Double altitude,
                                            Integer heartRate, Integer cadence, Double speed) {
        return ActivityTrackpoint.builder()
                .timestamp(START)
                .latitude(latitude)
                .longitude(longitude)
                .altitudeMeters(altitude)
                .heartRate(heartRate)
                .cadence(cadence)
                .speed(speed)
                .build();
    }

    private static class FakeElevationProvider implements ElevationProvider {

        private int calls;
        private List<Coordinate> lastCoordinates = List.of();

        @Override
        public List<Double> lookup(List<Coordinate> coordinates) {
            calls++;
            lastCoordinates = new ArrayList<>(coordinates);
            return coordinates.stream()
                    .map(coordinate -> 100.0 + (coordinate.latitude() - 40.0) * 1000.0)
                    .toList();
        }
    }
}
