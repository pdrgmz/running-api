package com.running.api.service;

import com.running.api.model.Activity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GpxParserServiceTest {

    private final GpxParserService service = new GpxParserService(null);

    private String gpx(String type) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test"
                 xmlns="http://www.topografix.com/GPX/1/1"
                 xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
              <metadata><time>2026-01-01T08:00:00Z</time></metadata>
              <trk>
                <type>%s</type>
                <trkseg>
                  <trkpt lat="40.0000" lon="-3.0000">
                    <ele>100.0</ele>
                    <time>2026-01-01T08:00:00Z</time>
                    <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>140</gpxtpx:hr><gpxtpx:cad>80</gpxtpx:cad></gpxtpx:TrackPointExtension></extensions>
                  </trkpt>
                  <trkpt lat="40.0008" lon="-3.0000">
                    <ele>101.0</ele>
                    <time>2026-01-01T08:00:30Z</time>
                    <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>160</gpxtpx:hr><gpxtpx:cad>84</gpxtpx:cad></gpxtpx:TrackPointExtension></extensions>
                  </trkpt>
                  <trkpt lat="40.0016" lon="-3.0000">
                    <ele>100.5</ele>
                    <time>2026-01-01T08:01:00Z</time>
                    <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>150</gpxtpx:hr><gpxtpx:cad>82</gpxtpx:cad></gpxtpx:TrackPointExtension></extensions>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
            """.formatted(type);
    }

    private InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesCoreMetrics() throws Exception {
        Activity activity = service.parse(stream(gpx("running")));

        assertThat(activity.getId()).isEqualTo("2026-01-01T08:00:00Z");
        assertThat(activity.getTrackpoints()).hasSize(3);
        assertThat(activity.getMaxHeartRate()).isEqualTo(160);
        assertThat(activity.getAvgHeartRate()).isEqualTo(150);
        assertThat(activity.getMaxCadence()).isEqualTo(84);
        assertThat(activity.getAvgCadence()).isEqualTo(82);
        assertThat(activity.getTotalTimeSeconds()).isCloseTo(60.0, within(1e-9));
        assertThat(activity.getElevationGain()).isCloseTo(1.0, within(1e-9));
        assertThat(activity.getElevationLoss()).isCloseTo(0.5, within(1e-9));
        // TRIMP = 1 min * (150 / 160) = 0.9375 -> 0.9
        assertThat(activity.getTrainingLoad()).isCloseTo(0.9, within(1e-9));
        // Dos segmentos Haversine de 0.0008 grados de latitud (~88.96 m cada uno)
        assertThat(activity.getDistanceMeters()).isCloseTo(177.92, within(1.0));
        assertThat(activity.getVo2MaxEstimated()).isCloseTo(31.1, within(0.2));
    }

    @Test
    void recognizesRunningActivity() {
        assertThat(GpxParserService.isRunningActivity(stream(gpx("running")))).isTrue();
    }

    @Test
    void rejectsNonRunningActivity() {
        assertThat(GpxParserService.isRunningActivity(stream(gpx("cycling")))).isFalse();
    }

    @Test
    void rejectsMalformedStream() {
        assertThat(GpxParserService.isRunningActivity(stream("<not-gpx/>"))).isFalse();
    }
}
