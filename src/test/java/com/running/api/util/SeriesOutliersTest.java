package com.running.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeriesOutliersTest {

    @Test
    void detectsAnIsolatedSpike() {
        double[] values = {140, 145, 210, 150, 155};

        boolean[] outliers = SeriesOutliers.detect(values, 10, 2, 50.0);

        assertThat(outliers).containsExactly(false, false, true, false, false);
    }

    @Test
    void detectsConsecutiveOutliersWhenTheyAreAMinority() {
        double[] values = {140, 145, 210, 215, 150, 155};

        boolean[] outliers = SeriesOutliers.detect(values, 10, 2, 50.0);

        assertThat(outliers).containsExactly(false, false, true, true, false, false);
    }

    @Test
    void keepsNormalVariationsInAVariableSeries() {
        double[] values = {1.0, 2.0, 4.0, 8.0, 7.0, 6.0};

        boolean[] outliers = SeriesOutliers.detect(values, 10, 2, 5.0);

        assertThat(outliers).containsOnly(false);
    }

    @Test
    void requiresEnoughNeighborsToFlagAValue() {
        double[] values = {200, 150};

        boolean[] outliers = SeriesOutliers.detect(values, 10, 2, 50.0);

        assertThat(outliers).containsOnly(false);
    }

    @Test
    void ignoresHolesInTheSeries() {
        double[] values = {140, Double.NaN, 210, Double.NaN, 155};

        boolean[] outliers = SeriesOutliers.detect(values, 10, 2, 50.0);

        assertThat(outliers).containsExactly(false, false, true, false, false);
    }

    @Test
    void returnsNoOutliersForNullOrEmptySeries() {
        assertThat(SeriesOutliers.detect(null, 10, 2, 50.0)).isEmpty();
        assertThat(SeriesOutliers.detect(new double[0], 10, 2, 50.0)).isEmpty();
    }
}
