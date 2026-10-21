package com.running.api.service;

import com.running.api.dto.TrackpointCompletionResultDto;
import com.running.api.dto.TrackpointMetricsCompletionResultDto;
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

    @Test
    void completeMetricsInterpolatesOnlyHeartRateCadenceAndSpeed() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 3.0);
        ActivityTrackpoint middle = point(null, null, null, null, null, null);
        ActivityTrackpoint last = point(40.002, -3.002, 200.0, 160, 90, 5.0);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(middle.getHeartRate()).isEqualTo(150);
        assertThat(middle.getCadence()).isEqualTo(85);
        assertThat(middle.getSpeed()).isCloseTo(4.0, within(1e-9));

        // Las coordenadas y la altitud no se tocan en este endpoint.
        assertThat(middle.getLatitude()).isNull();
        assertThat(middle.getLongitude()).isNull();
        assertThat(middle.getAltitudeMeters()).isNull();
        assertThat(elevationProvider.calls).isZero();

        assertThat(result.getActivityId()).isEqualTo("activity-1");
        assertThat(result.getTotalTrackpoints()).isEqualTo(3);
        assertThat(result.getHeartRateFilled()).isEqualTo(1);
        assertThat(result.getCadenceFilled()).isEqualTo(1);
        assertThat(result.getSpeedFilled()).isEqualTo(1);
        assertThat(result.getTotalFilled()).isEqualTo(3);
        assertThat(result.getMessage()).contains("puntos vecinos");
    }

    @Test
    void completeMetricsRemovesOutOfRangeValuesBeforeInterpolating() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 3.0);
        ActivityTrackpoint middle = point(40.001, -3.001, 110.0, 255, 300, 25.0);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, 160, 90, 5.0);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(middle.getHeartRate()).isEqualTo(150);
        assertThat(middle.getCadence()).isEqualTo(85);
        assertThat(middle.getSpeed()).isCloseTo(4.0, within(1e-9));

        assertThat(result.getHeartRateRemoved()).isEqualTo(1);
        assertThat(result.getCadenceRemoved()).isEqualTo(1);
        assertThat(result.getSpeedRemoved()).isEqualTo(1);
        assertThat(result.getTotalRemoved()).isEqualTo(3);
        assertThat(result.getTotalFilled()).isEqualTo(3);
        assertThat(result.getMessage()).contains("valores atípicos");
    }

    @Test
    void completeMetricsRemovesOutliersWithoutValidNeighbors() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 255, 300, 25.0);
        ActivityTrackpoint second = point(40.001, -3.001, 110.0, 255, 300, 25.0);
        Activity activity = activity(first, second);
        activity.setAvgHeartRate(255);
        activity.setMaxHeartRate(255);
        activity.setAvgCadence(300);
        activity.setMaxCadence(300);
        activity.setMaxSpeed(25.0);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(activity.getAvgHeartRate()).isNull();
        assertThat(activity.getMaxHeartRate()).isNull();
        assertThat(activity.getAvgCadence()).isNull();
        assertThat(activity.getMaxCadence()).isNull();
        assertThat(activity.getMaxSpeed()).isNull();
        assertThat(first.getHeartRate()).isNull();
        assertThat(first.getCadence()).isNull();
        assertThat(first.getSpeed()).isNull();
        assertThat(second.getHeartRate()).isNull();
        assertThat(second.getCadence()).isNull();
        assertThat(second.getSpeed()).isNull();
        assertThat(result.getTotalRemoved()).isEqualTo(6);
        assertThat(result.getTotalFilled()).isZero();
        assertThat(result.getMessage()).contains("no había puntos vecinos");
    }

    @Test
    void completeMetricsRemovesValuesVeryFarFromTheirNeighbors() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 160, 3.0);
        ActivityTrackpoint middle = point(40.001, -3.001, 110.0, 210, 220, 10.0);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, 160, 170, 4.0);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        // Valores dentro de los rangos fisiológicos, pero muy lejos de sus vecinos.
        assertThat(middle.getHeartRate()).isEqualTo(150);
        assertThat(middle.getCadence()).isEqualTo(165);
        assertThat(middle.getSpeed()).isCloseTo(3.5, within(1e-9));

        assertThat(result.getHeartRateRemoved()).isEqualTo(1);
        assertThat(result.getCadenceRemoved()).isEqualTo(1);
        assertThat(result.getSpeedRemoved()).isEqualTo(1);
        assertThat(result.getTotalRemoved()).isEqualTo(3);
        assertThat(result.getTotalFilled()).isEqualTo(3);
    }

    @Test
    void completeMetricsKeepsValuesCloseToTheirNeighbors() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 160, 3.0);
        ActivityTrackpoint middle = point(40.001, -3.001, 110.0, 180, 190, 7.0);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, 160, 170, 4.0);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(middle.getHeartRate()).isEqualTo(180);
        assertThat(middle.getCadence()).isEqualTo(190);
        assertThat(middle.getSpeed()).isCloseTo(7.0, within(1e-9));
        assertThat(result.getTotalRemoved()).isZero();
        assertThat(result.getTotalFilled()).isZero();
        assertThat(result.getMessage()).isEqualTo("No había datos que completar");
    }

    @Test
    void completeMetricsKeepsZeroSpeedAndLowCadence() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 25, 10.0);
        ActivityTrackpoint middle = point(40.001, -3.001, 110.0, 140, 25, 0.0);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, 140, 26, 10.0);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        // Una parada (velocidad 0) y una cadencia baja son valores válidos, no huecos,
        // aunque se alejen de sus vecinos.
        assertThat(middle.getSpeed()).isZero();
        assertThat(middle.getCadence()).isEqualTo(25);
        assertThat(result.getCadenceRemoved()).isZero();
        assertThat(result.getSpeedRemoved()).isZero();
        assertThat(result.getTotalFilled()).isZero();
    }

    @Test
    void completeMetricsNeverChangesCoordinatesOrAltitude() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 3.0);
        ActivityTrackpoint middle = point(999.0, 999.0, 9999.0, null, null, null);
        ActivityTrackpoint last = point(40.002, -3.002, 200.0, 160, 90, 5.0);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        service.completeMetrics(activity);

        assertThat(first.getLatitude()).isEqualTo(40.000);
        assertThat(first.getLongitude()).isEqualTo(-3.000);
        assertThat(first.getAltitudeMeters()).isEqualTo(100.0);
        assertThat(middle.getLatitude()).isEqualTo(999.0);
        assertThat(middle.getLongitude()).isEqualTo(999.0);
        assertThat(middle.getAltitudeMeters()).isEqualTo(9999.0);
        assertThat(last.getLatitude()).isEqualTo(40.002);
        assertThat(last.getLongitude()).isEqualTo(-3.002);
        assertThat(last.getAltitudeMeters()).isEqualTo(200.0);
        assertThat(first.getElevationCalculated()).isNull();
        assertThat(middle.getElevationCalculated()).isNull();
        assertThat(last.getElevationCalculated()).isNull();
    }

    @Test
    void completeAlsoRemovesOutOfRangeMetrics() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 3.0);
        ActivityTrackpoint middle = point(40.001, -3.001, 110.0, 255, 300, 25.0);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, 160, 90, 5.0);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointCompletionResultDto result = service.complete(activity);

        assertThat(middle.getHeartRate()).isEqualTo(150);
        assertThat(middle.getCadence()).isEqualTo(85);
        assertThat(middle.getSpeed()).isCloseTo(4.0, within(1e-9));
        assertThat(result.getHeartRateRemoved()).isEqualTo(1);
        assertThat(result.getCadenceRemoved()).isEqualTo(1);
        assertThat(result.getSpeedRemoved()).isEqualTo(1);
        assertThat(result.getTotalRemoved()).isEqualTo(3);
    }

    @Test
    void completeMetricsFillsEdgesWithNearestValidValue() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, null, null, null);
        ActivityTrackpoint middle = point(40.001, -3.001, 110.0, 150, 85, 3.5);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, null, null, null);
        Activity activity = activity(first, middle, last);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(first.getHeartRate()).isEqualTo(150);
        assertThat(first.getCadence()).isEqualTo(85);
        assertThat(first.getSpeed()).isEqualTo(3.5);
        assertThat(last.getHeartRate()).isEqualTo(150);
        assertThat(last.getCadence()).isEqualTo(85);
        assertThat(last.getSpeed()).isEqualTo(3.5);
        assertThat(result.getTotalFilled()).isEqualTo(6);

        // La latitud/longitud/altitud originales siguen intactas.
        assertThat(first.getLatitude()).isEqualTo(40.000);
        assertThat(first.getLongitude()).isEqualTo(-3.000);
        assertThat(first.getAltitudeMeters()).isEqualTo(100.0);
        assertThat(last.getAltitudeMeters()).isEqualTo(120.0);
    }

    @Test
    void completeMetricsDoesNothingWhenThereAreNoValidValues() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, null, null, null);
        ActivityTrackpoint second = point(40.001, -3.001, 110.0, null, null, null);
        Activity activity = activity(first, second);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(first.getHeartRate()).isNull();
        assertThat(first.getCadence()).isNull();
        assertThat(first.getSpeed()).isNull();
        assertThat(second.getHeartRate()).isNull();
        assertThat(result.getTotalFilled()).isZero();
        assertThat(result.getMessage()).isEqualTo("No había datos que completar");
    }

    @Test
    void completeMetricsUpdatesActivitySummary() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 3.0);
        ActivityTrackpoint middle = point(40.001, -3.001, 110.0, null, null, null);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, 160, 90, 5.0);
        Activity activity = activity(first, middle, last);
        activity.setDistanceMeters(1000.0);
        activity.setTotalTimeSeconds(300.0);
        activity.setMaxHeartRate(255);
        activity.setMaxCadence(300);
        activity.setMaxSpeed(25.0);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        service.completeMetrics(activity);

        assertThat(activity.getAvgHeartRate()).isEqualTo(150);
        assertThat(activity.getMaxHeartRate()).isEqualTo(160);
        assertThat(activity.getAvgCadence()).isEqualTo(85);
        assertThat(activity.getMaxCadence()).isEqualTo(90);
        assertThat(activity.getMaxSpeed()).isCloseTo(5.0, within(1e-9));
        assertThat(activity.getAvgSpeed()).isCloseTo(1000.0 / 300.0, within(1e-9));

        // El resumen de elevación no se recalcula en este endpoint.
        assertThat(activity.getElevationGain()).isNull();
        assertThat(activity.getElevationLoss()).isNull();
    }

    @Test
    void completeMetricsKeepsValidActivitySummaryValues() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 3.0);
        ActivityTrackpoint middle = point(40.001, -3.001, 110.0, 150, 85, 4.0);
        ActivityTrackpoint last = point(40.002, -3.002, 120.0, 160, 90, 5.0);
        Activity activity = activity(first, middle, last);
        activity.setAvgHeartRate(145);
        activity.setMaxHeartRate(170);
        activity.setAvgCadence(82);
        activity.setMaxCadence(95);
        activity.setMaxSpeed(5.5);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        // Sin huecos ni atípicos, los valores válidos del resumen se conservan.
        assertThat(activity.getAvgHeartRate()).isEqualTo(145);
        assertThat(activity.getMaxHeartRate()).isEqualTo(170);
        assertThat(activity.getAvgCadence()).isEqualTo(82);
        assertThat(activity.getMaxCadence()).isEqualTo(95);
        assertThat(activity.getMaxSpeed()).isCloseTo(5.5, within(1e-9));
        assertThat(result.getTotalFilled()).isZero();
        assertThat(result.getTotalRemoved()).isZero();
    }

    @Test
    void completeMetricsAdjustsImpossibleDistanceJumps() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 10.0);
        first.setTimestamp(START);
        first.setDistanceMeters(0.0);

        ActivityTrackpoint second = point(40.001, -3.001, 110.0, 145, 82, 10.0);
        second.setTimestamp(START.plusSeconds(10));
        second.setDistanceMeters(100.0);

        ActivityTrackpoint third = point(40.002, -3.002, 120.0, 150, 84, 10.0);
        third.setTimestamp(START.plusSeconds(11));
        third.setDistanceMeters(5000.0);

        ActivityTrackpoint fourth = point(40.003, -3.003, 130.0, 155, 86, 10.0);
        fourth.setTimestamp(START.plusSeconds(21));
        fourth.setDistanceMeters(5010.0);

        Activity activity = activity(first, second, third, fourth);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(first.getDistanceMeters()).isEqualTo(0.0);
        assertThat(second.getDistanceMeters()).isEqualTo(100.0);
        assertThat(third.getDistanceMeters()).isEqualTo(100.0);
        assertThat(fourth.getDistanceMeters()).isEqualTo(110.0);

        assertThat(result.getDistanceAdjusted()).isEqualTo(2);
        assertThat(result.getDistanceDiscardedMeters()).isEqualTo(4900.0);
        assertThat(result.getMessage()).contains("se ajustaron 2 distancias");
    }

    @Test
    void completeMetricsKeepsNormalDistancesUntouched() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 10.0);
        first.setTimestamp(START);
        first.setDistanceMeters(0.0);

        ActivityTrackpoint second = point(40.001, -3.001, 110.0, 145, 82, 10.0);
        second.setTimestamp(START.plusSeconds(10));
        second.setDistanceMeters(100.0);

        ActivityTrackpoint third = point(40.002, -3.002, 120.0, 150, 84, 10.0);
        third.setTimestamp(START.plusSeconds(20));
        third.setDistanceMeters(200.0);

        Activity activity = activity(first, second, third);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(second.getDistanceMeters()).isEqualTo(100.0);
        assertThat(third.getDistanceMeters()).isEqualTo(200.0);
        assertThat(result.getDistanceAdjusted()).isZero();
        assertThat(result.getDistanceDiscardedMeters()).isZero();
    }

    @Test
    void completeMetricsTreatsDistanceCounterResetAsZeroIncrement() {
        ActivityTrackpoint first = point(40.000, -3.000, 100.0, 140, 80, 10.0);
        first.setTimestamp(START);
        first.setDistanceMeters(0.0);

        ActivityTrackpoint second = point(40.001, -3.001, 110.0, 145, 82, 10.0);
        second.setTimestamp(START.plusSeconds(10));
        second.setDistanceMeters(100.0);

        ActivityTrackpoint third = point(40.002, -3.002, 120.0, 150, 84, 5.0);
        third.setTimestamp(START.plusSeconds(20));
        third.setDistanceMeters(50.0);

        ActivityTrackpoint fourth = point(40.003, -3.003, 130.0, 155, 86, 5.0);
        fourth.setTimestamp(START.plusSeconds(30));
        fourth.setDistanceMeters(60.0);

        Activity activity = activity(first, second, third, fourth);

        TrackpointCompletionService service = new TrackpointCompletionService(elevationProvider, 10, 0.1);
        TrackpointMetricsCompletionResultDto result = service.completeMetrics(activity);

        assertThat(third.getDistanceMeters()).isEqualTo(100.0);
        assertThat(fourth.getDistanceMeters()).isEqualTo(110.0);
        assertThat(result.getDistanceAdjusted()).isEqualTo(2);
        assertThat(result.getDistanceDiscardedMeters()).isZero();
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
