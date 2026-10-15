package com.running.api.service;

import com.running.api.model.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TcxParserServiceTest {

    private TcxParserService service;

    @BeforeEach
    void setUp() {
        service = new TcxParserService(null);
        // El umbral se inyecta con @Value en runtime; en el test lo fijamos explícitamente.
        ReflectionTestUtils.setField(service, "elevationThreshold", 0.1);
    }

    private String tcx(String sport) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="%s">
                  <Id>2026-01-01T08:00:00Z</Id>
                  <Lap StartTime="2026-01-01T08:00:00Z">
                    <TotalTimeSeconds>60.0</TotalTimeSeconds>
                    <DistanceMeters>222.4</DistanceMeters>
                    <Calories>30</Calories>
                    <Track>
                      <Trackpoint>
                        <Time>2026-01-01T08:00:00Z</Time>
                        <Position><LatitudeDegrees>40.0</LatitudeDegrees><LongitudeDegrees>-3.0</LongitudeDegrees></Position>
                        <AltitudeMeters>100.0</AltitudeMeters>
                        <DistanceMeters>0.0</DistanceMeters>
                        <HeartRateBpm><Value>140</Value></HeartRateBpm>
                        <Cadence>80</Cadence>
                        <Extensions><TPX xmlns="http://www.garmin.com/xmlschemas/ActivityExtension/v2"><Speed>3.7</Speed></TPX></Extensions>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-01-01T08:00:30Z</Time>
                        <Position><LatitudeDegrees>40.001</LatitudeDegrees><LongitudeDegrees>-3.0</LongitudeDegrees></Position>
                        <AltitudeMeters>101.0</AltitudeMeters>
                        <DistanceMeters>111.2</DistanceMeters>
                        <HeartRateBpm><Value>160</Value></HeartRateBpm>
                        <Cadence>84</Cadence>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-01-01T08:01:00Z</Time>
                        <Position><LatitudeDegrees>40.002</LatitudeDegrees><LongitudeDegrees>-3.0</LongitudeDegrees></Position>
                        <AltitudeMeters>100.5</AltitudeMeters>
                        <DistanceMeters>222.4</DistanceMeters>
                        <HeartRateBpm><Value>150</Value></HeartRateBpm>
                        <Cadence>82</Cadence>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
            """.formatted(sport);
    }

    private InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesCoreMetrics() throws Exception {
        Activity activity = service.parse(stream(tcx("Running")));

        assertThat(activity.getTrackpoints()).hasSize(3);
        assertThat(activity.getDistanceMeters()).isCloseTo(222.4, within(1e-9));
        assertThat(activity.getTotalTimeSeconds()).isCloseTo(60.0, within(1e-9));
        assertThat(activity.getTotalCalories()).isEqualTo(30);
        assertThat(activity.getMaxHeartRate()).isEqualTo(160);
        assertThat(activity.getAvgHeartRate()).isEqualTo(150);
        assertThat(activity.getMaxCadence()).isEqualTo(84);
        assertThat(activity.getAvgCadence()).isEqualTo(82);
        assertThat(activity.getElevationGain()).isCloseTo(1.0, within(1e-9));
        assertThat(activity.getElevationLoss()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void recognizesRunningActivity() {
        assertThat(TcxParserService.isRunningActivity(stream(tcx("Running")))).isTrue();
    }

    @Test
    void rejectsNonRunningActivity() {
        assertThat(TcxParserService.isRunningActivity(stream(tcx("Biking")))).isFalse();
    }
}
