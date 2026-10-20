package com.running.api.service;

import com.running.api.dto.ElevationCoverageDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityServiceTest {

    @Test
    void calculatesElevationCoveragePercentages() {
        ElevationCoverageDto coverage = ActivityService.buildElevationCoverage(200, 150);

        assertThat(coverage.getTotalTrackpoints()).isEqualTo(200);
        assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(150);
        assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(50);
        assertThat(coverage.getWithElevationPercent()).isEqualTo(75.0);
        assertThat(coverage.getMissingElevationPercent()).isEqualTo(25.0);
    }

    @Test
    void roundsPercentagesToTwoDecimals() {
        ElevationCoverageDto coverage = ActivityService.buildElevationCoverage(3, 1);

        assertThat(coverage.getWithElevationPercent()).isEqualTo(33.33);
        assertThat(coverage.getMissingElevationPercent()).isEqualTo(66.67);
    }

    @Test
    void returnsZeroPercentagesWhenThereAreNoTrackpoints() {
        ElevationCoverageDto coverage = ActivityService.buildElevationCoverage(0, 0);

        assertThat(coverage.getTotalTrackpoints()).isZero();
        assertThat(coverage.getTrackpointsWithElevation()).isZero();
        assertThat(coverage.getTrackpointsMissingElevation()).isZero();
        assertThat(coverage.getWithElevationPercent()).isZero();
        assertThat(coverage.getMissingElevationPercent()).isZero();
    }

    @Test
    void clampsInconsistentCounts() {
        ElevationCoverageDto coverage = ActivityService.buildElevationCoverage(10, 12);

        assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(10);
        assertThat(coverage.getTrackpointsMissingElevation()).isZero();
        assertThat(coverage.getWithElevationPercent()).isEqualTo(100.0);
        assertThat(coverage.getMissingElevationPercent()).isZero();
    }
}
