package com.running.api.service;

import com.running.api.dto.ElevationCoverageDto;
import com.running.api.repository.ActivityTrackpointRepository;
import com.running.api.repository.projection.ElevationCoverageProjection;
import com.running.api.repository.projection.FlatElevationProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalActivityServiceTest {

        @Mock
        private ActivityTrackpointRepository trackpointRepository;

        @InjectMocks
        private GlobalActivityService globalActivityService;

        @Test
        void calculatesElevationCoveragePercentages() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(200, 150, 0);

                assertThat(coverage.getTotalTrackpoints()).isEqualTo(200);
                assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(150);
                assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(50);
                assertThat(coverage.getTrackpointsWithFlatElevation()).isZero();
                assertThat(coverage.getWithElevationPercent()).isEqualTo(75.0);
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(25.0);
                assertThat(coverage.getFlatElevationPercent()).isZero();
        }

        @Test
        void roundsPercentagesToTwoDecimals() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(3, 1, 0);

                assertThat(coverage.getWithElevationPercent()).isEqualTo(33.33);
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(66.67);
        }

        @Test
        void returnsZeroPercentagesWhenThereAreNoTrackpoints() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(0, 0, 0);

                assertThat(coverage.getTotalTrackpoints()).isZero();
                assertThat(coverage.getTrackpointsWithElevation()).isZero();
                assertThat(coverage.getTrackpointsMissingElevation()).isZero();
                assertThat(coverage.getTrackpointsWithFlatElevation()).isZero();
                assertThat(coverage.getWithElevationPercent()).isZero();
                assertThat(coverage.getMissingElevationPercent()).isZero();
                assertThat(coverage.getFlatElevationPercent()).isZero();
        }

        @Test
        void clampsInconsistentCounts() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(10, 12, 0);

                assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(10);
                assertThat(coverage.getTrackpointsMissingElevation()).isZero();
                assertThat(coverage.getWithElevationPercent()).isEqualTo(100.0);
                assertThat(coverage.getMissingElevationPercent()).isZero();
        }

        @Test
        void subtractsFlatAltitudesFromTheValidCountAndCountsThemAsPending() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(200, 150, 40);

                assertThat(coverage.getTotalTrackpoints()).isEqualTo(200);
                assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(110);
                assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(90);
                assertThat(coverage.getTrackpointsWithFlatElevation()).isEqualTo(40);
                assertThat(coverage.getWithElevationPercent()).isEqualTo(55.0);
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(45.0);
                assertThat(coverage.getFlatElevationPercent()).isEqualTo(20.0);
        }

        @Test
        void clampsFlatAltitudesToTheValidCount() {
                ElevationCoverageDto coverage = GlobalActivityService.buildElevationCoverage(10, 5, 8);

                assertThat(coverage.getTrackpointsWithElevation()).isZero();
                assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(10);
                assertThat(coverage.getTrackpointsWithFlatElevation()).isEqualTo(5);
                assertThat(coverage.getWithElevationPercent()).isZero();
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(100.0);
                assertThat(coverage.getFlatElevationPercent()).isEqualTo(50.0);
        }

        @Test
        void coverageTreatsFlatAltitudesAsPendingAndReportsThemSeparately() {
                when(trackpointRepository.getElevationCoverage(eq(1L), anyDouble()))
                                .thenReturn(coverageProjection(200L, 150L));
                when(trackpointRepository.findFlatElevationPoints(eq(1L), anyDouble(), anyInt(), anyInt()))
                                .thenReturn(List.of(flatGroup("a", 30L), flatGroup("b", 10L)));

                ElevationCoverageDto coverage = globalActivityService.getElevationCoverage(1L);

                assertThat(coverage.getTotalTrackpoints()).isEqualTo(200);
                assertThat(coverage.getTrackpointsWithElevation()).isEqualTo(110);
                assertThat(coverage.getTrackpointsMissingElevation()).isEqualTo(90);
                assertThat(coverage.getTrackpointsWithFlatElevation()).isEqualTo(40);
                assertThat(coverage.getWithElevationPercent()).isEqualTo(55.0);
                assertThat(coverage.getMissingElevationPercent()).isEqualTo(45.0);
                assertThat(coverage.getFlatElevationPercent()).isEqualTo(20.0);
        }

        private static ElevationCoverageProjection coverageProjection(Long total, Long withElevation) {
                return new ElevationCoverageProjection() {
                        @Override
                        public Long getTotalTrackpoints() {
                                return total;
                        }

                        @Override
                        public Long getTrackpointsWithElevation() {
                                return withElevation;
                        }
                };
        }

        private static FlatElevationProjection flatGroup(String activityId, Long occurrences) {
                return new FlatElevationProjection() {
                        @Override
                        public String getActivityId() {
                                return activityId;
                        }

                        @Override
                        public Long getOccurrences() {
                                return occurrences;
                        }
                };
        }
}
