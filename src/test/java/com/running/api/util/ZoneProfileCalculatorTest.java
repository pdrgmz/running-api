package com.running.api.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.running.api.dto.HeartRateZoneDto;
import com.running.api.dto.PaceZoneDto;
import com.running.api.model.HrZoneMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZoneProfileCalculatorTest {

    @Test
    void buildsHeartRateZonesAsPercentagesOfMaxHeartRate() {
        List<HeartRateZoneDto> zones = ZoneProfileCalculator.standardHeartRateZones(
                190, 50, HrZoneMethod.MAX_HR_PERCENT);

        assertThat(zones).hasSize(5);
        assertThat(zones.get(0).minBpm()).isEqualTo(95);
        assertThat(zones.get(0).maxBpm()).isEqualTo(114);
        assertThat(zones.get(0).minPercent()).isEqualTo(50.0);
        assertThat(zones.get(4).minBpm()).isEqualTo(171);
        assertThat(zones.get(4).maxBpm()).isEqualTo(190);
    }

    @Test
    void buildsHeartRateZonesFromHeartRateReserve() {
        List<HeartRateZoneDto> zones = ZoneProfileCalculator.standardHeartRateZones(
                190, 50, HrZoneMethod.HEART_RATE_RESERVE);

        assertThat(zones.get(0).minBpm()).isEqualTo(120);
        assertThat(zones.get(0).maxBpm()).isEqualTo(134);
        assertThat(zones.get(0).minPercent()).isEqualTo(50.0);
        assertThat(zones.get(0).maxPercent()).isEqualTo(60.0);
    }

    @Test
    void buildsPaceZonesAroundThresholdPace() {
        List<PaceZoneDto> zones = ZoneProfileCalculator.standardPaceZones(300.0);

        assertThat(zones).hasSize(5);
        assertThat(zones.get(0).fastestPaceSecondsPerKm()).isEqualTo(390.0);
        assertThat(zones.get(0).slowestPaceSecondsPerKm()).isEqualTo(450.0);
        assertThat(zones.get(0).fastestPaceFormatted()).isEqualTo("6:30");
        assertThat(zones.get(0).slowestPaceFormatted()).isEqualTo("7:30");
        assertThat(zones.get(3).fastestPaceSecondsPerKm()).isEqualTo(285.0);
        assertThat(zones.get(3).slowestPaceSecondsPerKm()).isEqualTo(315.0);
        assertThat(zones.get(4).fastestPaceSecondsPerKm()).isEqualTo(255.0);
        assertThat(zones.get(4).slowestPaceSecondsPerKm()).isEqualTo(285.0);
    }

    @Test
    void formatsPaceAsMinutesAndSeconds() {
        assertThat(ZoneProfileCalculator.formatPace(270.0)).isEqualTo("4:30");
        assertThat(ZoneProfileCalculator.formatPace(288.4)).isEqualTo("4:48");
        assertThat(ZoneProfileCalculator.formatPace(1800.0)).isEqualTo("30:00");
    }

    @Test
    void rejectsStandardHeartRateZonesWithCustomMethod() {
        assertThatThrownBy(() -> ZoneProfileCalculator.standardHeartRateZones(
                190, 50, HrZoneMethod.CUSTOM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
