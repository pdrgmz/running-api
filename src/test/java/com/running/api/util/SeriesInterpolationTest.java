package com.running.api.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeriesInterpolationTest {

    @Test
    void fillsInteriorGapsLinearly() {
        double[] values = {10.0, Double.NaN, 30.0, Double.NaN, Double.NaN, 60.0};

        int filled = SeriesInterpolation.fillMissing(values);

        assertThat(filled).isEqualTo(3);
        assertThat(values).containsExactly(10.0, 20.0, 30.0, 40.0, 50.0, 60.0);
    }

    @Test
    void fillsEdgesWithNearestValidValue() {
        double[] values = {Double.NaN, Double.NaN, 10.0, Double.NaN, 20.0, Double.NaN};

        int filled = SeriesInterpolation.fillMissing(values);

        assertThat(filled).isEqualTo(4);
        assertThat(values[0]).isEqualTo(10.0);
        assertThat(values[1]).isEqualTo(10.0);
        assertThat(values[3]).isCloseTo(15.0, within(1e-9));
        assertThat(values[5]).isEqualTo(20.0);
    }

    @Test
    void singleValidValueFillsWholeSeries() {
        double[] values = {Double.NaN, 5.0, Double.NaN, Double.NaN};

        int filled = SeriesInterpolation.fillMissing(values);

        assertThat(filled).isEqualTo(3);
        assertThat(values).containsExactly(5.0, 5.0, 5.0, 5.0);
    }

    @Test
    void returnsZeroWhenThereIsNoValidValue() {
        double[] values = {Double.NaN, Double.NaN, Double.NaN};

        int filled = SeriesInterpolation.fillMissing(values);

        assertThat(filled).isZero();
        assertThat(Arrays.stream(values).allMatch(Double::isNaN)).isTrue();
    }

    @Test
    void fillsOnlyInteriorGapsLeavingEdgesUntouched() {
        double[] values = {Double.NaN, 10.0, Double.NaN, 20.0, Double.NaN};

        int filled = SeriesInterpolation.fillMissingInterior(values);

        assertThat(filled).isEqualTo(1);
        assertThat(values[0]).isNaN();
        assertThat(values[1]).isEqualTo(10.0);
        assertThat(values[2]).isCloseTo(15.0, within(1e-9));
        assertThat(values[3]).isEqualTo(20.0);
        assertThat(values[4]).isNaN();
    }

    @Test
    void doesNotFillAnythingWithASingleValidValue() {
        double[] values = {Double.NaN, 5.0, Double.NaN};

        int filled = SeriesInterpolation.fillMissingInterior(values);

        assertThat(filled).isZero();
        assertThat(values[0]).isNaN();
        assertThat(values[1]).isEqualTo(5.0);
        assertThat(values[2]).isNaN();
    }
}
