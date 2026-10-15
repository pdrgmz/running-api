package com.running.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ElevationAccumulatorTest {

    @Test
    void firstSampleIsReferenceAndAddsNothing() {
        ElevationAccumulator accumulator = new ElevationAccumulator(0.5);

        accumulator.add(100.0);

        assertThat(accumulator.getGain()).isEqualTo(0.0);
        assertThat(accumulator.getLoss()).isEqualTo(0.0);
    }

    @Test
    void ignoresNoiseBelowThreshold() {
        ElevationAccumulator accumulator = new ElevationAccumulator(0.5);

        accumulator.add(100.0);
        accumulator.add(100.2);
        accumulator.add(100.4);

        assertThat(accumulator.getGain()).isEqualTo(0.0);
        assertThat(accumulator.getLoss()).isEqualTo(0.0);
    }

    @Test
    void accumulatesGainWhenThresholdExceeded() {
        ElevationAccumulator accumulator = new ElevationAccumulator(0.5);

        accumulator.add(100.0);
        accumulator.add(100.2);
        accumulator.add(100.4);
        accumulator.add(100.9);

        assertThat(accumulator.getGain()).isCloseTo(0.9, within(1e-9));
        assertThat(accumulator.getLoss()).isEqualTo(0.0);
    }

    @Test
    void accumulatesLossOnDescent() {
        ElevationAccumulator accumulator = new ElevationAccumulator(0.5);

        accumulator.add(100.0);
        accumulator.add(99.0);

        assertThat(accumulator.getGain()).isEqualTo(0.0);
        assertThat(accumulator.getLoss()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void skipsNoDataSamples() {
        ElevationAccumulator accumulator = new ElevationAccumulator(0.5);

        accumulator.add(100.0);
        accumulator.add(ElevationAccumulator.NO_DATA);
        accumulator.add(101.0);

        assertThat(accumulator.getGain()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void zeroThresholdAccumulatesEveryChange() {
        ElevationAccumulator accumulator = new ElevationAccumulator(0.0);

        accumulator.add(100.0);
        accumulator.add(100.1);
        accumulator.add(99.9);

        assertThat(accumulator.getGain()).isCloseTo(0.1, within(1e-9));
        assertThat(accumulator.getLoss()).isCloseTo(0.2, within(1e-9));
    }
}
