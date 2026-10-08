package com.running.api.service;

import com.running.api.model.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class TcxParserServiceTest {

    @Mock
    private AnalyticsService analyticsService;

    private TcxParserService tcxParserService;

    @BeforeEach
    void setUp() {
        tcxParserService = new TcxParserService(analyticsService);
    }

    @Test
    void testParseTcxCadenceAndPace() throws Exception {
        String tcxXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                  <Activities>
                    <Activity Sport="Running">
                      <Id>2026-10-07T10:00:00Z</Id>
                      <Lap StartTime="2026-10-07T10:00:00Z">
                        <TotalTimeSeconds>300.0</TotalTimeSeconds>
                        <DistanceMeters>1000.0</DistanceMeters>
                        <Calories>70</Calories>
                        <Track>
                          <Trackpoint>
                            <Time>2026-10-07T10:00:00Z</Time>
                            <DistanceMeters>0.0</DistanceMeters>
                            <HeartRateBpm><Value>140</Value></HeartRateBpm>
                            <Cadence>160</Cadence>
                          </Trackpoint>
                          <Trackpoint>
                            <Time>2026-10-07T10:02:30Z</Time>
                            <DistanceMeters>500.0</DistanceMeters>
                            <HeartRateBpm><Value>150</Value></HeartRateBpm>
                            <Cadence>170</Cadence>
                          </Trackpoint>
                          <Trackpoint>
                            <Time>2026-10-07T10:05:00Z</Time>
                            <DistanceMeters>1000.0</DistanceMeters>
                            <HeartRateBpm><Value>160</Value></HeartRateBpm>
                            <Cadence>180</Cadence>
                          </Trackpoint>
                        </Track>
                      </Lap>
                    </Activity>
                  </Activities>
                </TrainingCenterDatabase>
                """;

        InputStream is = new ByteArrayInputStream(tcxXml.getBytes(StandardCharsets.UTF_8));
        Activity activity = tcxParserService.parse(is);

        assertNotNull(activity);
        assertEquals("2026-10-07T10:00:00Z", activity.getId());
        assertEquals(1000.0, activity.getDistanceMeters());
        assertEquals(300.0, activity.getTotalTimeSeconds());

        // Cadence check
        assertEquals(170, activity.getAvgCadence());
        assertEquals(180, activity.getMaxCadence());
    }
}
