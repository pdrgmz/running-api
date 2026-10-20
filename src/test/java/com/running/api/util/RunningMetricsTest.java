package com.running.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RunningMetricsTest {

    @Test
    void trainingLoadIsDurationTimesHeartRateRatio() {
        // 60 min * (150 / 180) = 50.0
        assertThat(RunningMetrics.trainingLoad(60.0, 150.0, 180)).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void trainingLoadReturnsNullWithoutData() {
        assertThat(RunningMetrics.trainingLoad(0.0, 150.0, 180)).isNull();
        assertThat(RunningMetrics.trainingLoad(60.0, 0.0, 180)).isNull();
        assertThat(RunningMetrics.trainingLoad(60.0, 150.0, 0)).isNull();
        assertThat(RunningMetrics.trainingLoad(60.0, 180.0, 180)).isNull();
    }

    @Test
    void vamUsesGradientWhenAvailable() {
        // 5 m/s -> 18 km/h ; pendiente 100/5000 = 0.02 ; factor 1 + 0.002 = 1.002
        assertThat(RunningMetrics.vamKmH(5.0, 5000.0, 100.0)).isCloseTo(18.0, within(1e-9));
    }

    @Test
    void vamWithoutGradientReturnsFlatSpeed() {
        assertThat(RunningMetrics.vamKmH(5.0, null, null)).isCloseTo(18.0, within(1e-9));
        assertThat(RunningMetrics.vamKmH(5.0, 0.0, 0.0)).isCloseTo(18.0, within(1e-9));
    }

    @Test
    void vamIsNullWithoutSpeed() {
        assertThat(RunningMetrics.vamKmH(0.0, 5000.0, 100.0)).isNull();
    }

    @Test
    void vo2MaxUsesDanielsGilbertFormula() {
        // 5 m/s -> 300 m/min -> VO2 = 59.4 mL/kg/min
        assertThat(RunningMetrics.vo2Max(5.0)).isCloseTo(59.4, within(1e-9));
    }

    @Test
    void vo2MaxIsNullBelowSpeedThreshold() {
        assertThat(RunningMetrics.vo2Max(1.0)).isNull();
        assertThat(RunningMetrics.vo2Max(0.5)).isNull();
        assertThat(RunningMetrics.vo2Max(0.0)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"running", "Running", "RUNNING", "trail_running", "run", "carrera"})
    void recognizesRunningSports(String sport) {
        assertThat(RunningMetrics.isRunningSport(sport)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cycling", "swimming", "walking", "hiking", "Biking"})
    void rejectsOtherSports(String sport) {
        assertThat(RunningMetrics.isRunningSport(sport)).isFalse();
    }

    @Test
    void nullSportIsNotRunning() {
        assertThat(RunningMetrics.isRunningSport(null)).isFalse();
    }

    @Test
    void validatesPlausibleHeartRate() {
        assertThat(RunningMetrics.isPlausibleHeartRate(30)).isTrue();
        assertThat(RunningMetrics.isPlausibleHeartRate(240)).isTrue();
        assertThat(RunningMetrics.isPlausibleHeartRate(29)).isFalse();
        assertThat(RunningMetrics.isPlausibleHeartRate(241)).isFalse();
        assertThat(RunningMetrics.isPlausibleHeartRate(null)).isFalse();
    }

    @Test
    void validatesPlausibleCadence() {
        assertThat(RunningMetrics.isPlausibleCadence(30)).isTrue();
        assertThat(RunningMetrics.isPlausibleCadence(240)).isTrue();
        assertThat(RunningMetrics.isPlausibleCadence(29)).isFalse();
        assertThat(RunningMetrics.isPlausibleCadence(241)).isFalse();
        assertThat(RunningMetrics.isPlausibleCadence(null)).isFalse();
    }

    @Test
    void validatesPlausibleSpeed() {
        assertThat(RunningMetrics.isPlausibleSpeed(0.1)).isTrue();
        assertThat(RunningMetrics.isPlausibleSpeed(15.0)).isTrue();
        assertThat(RunningMetrics.isPlausibleSpeed(0.0)).isFalse();
        assertThat(RunningMetrics.isPlausibleSpeed(-1.0)).isFalse();
        assertThat(RunningMetrics.isPlausibleSpeed(15.1)).isFalse();
        assertThat(RunningMetrics.isPlausibleSpeed(Double.NaN)).isFalse();
        assertThat(RunningMetrics.isPlausibleSpeed(null)).isFalse();
    }
}
