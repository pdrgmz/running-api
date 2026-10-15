package com.running.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    @Test
    void distanceBetweenSamePointIsZero() {
        assertThat(GeoUtils.haversineMeters(40.0, -3.0, 40.0, -3.0)).isEqualTo(0.0);
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertThat(GeoUtils.haversineMeters(0.0, 0.0, 1.0, 0.0)).isCloseTo(111194.9, within(1.0));
    }

    @Test
    void smallDistanceIsProportionalToLatitudeDelta() {
        // 0.0009 grados ~ 100 m
        assertThat(GeoUtils.haversineMeters(40.0, -3.0, 40.0009, -3.0)).isCloseTo(100.07, within(0.5));
    }

    @Test
    void distanceIsSymmetric() {
        double madridToBarcelona = GeoUtils.haversineMeters(40.4168, -3.7038, 41.3874, 2.1686);
        double barcelonaToMadrid = GeoUtils.haversineMeters(41.3874, 2.1686, 40.4168, -3.7038);
        assertThat(madridToBarcelona).isCloseTo(barcelonaToMadrid, within(1e-6));
    }
}
