package com.running.api.service;

import com.running.api.dto.GeoJsonDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import com.running.api.repository.ActivityTrackpointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityTrackpointRepository trackpointRepository;

    @Test
    void escapesXmlReservedCharacters() {
        assertThat(GeoService.escapeXml("A&B <C> \"D\" 'E'"))
                .isEqualTo("A&amp;B &lt;C&gt; &quot;D&quot; &apos;E&apos;");
        assertThat(GeoService.escapeXml(null)).isEmpty();
    }

    @Test
    void exportGpxEscapesNameAndOmitsMissingTimes() {
        GeoService service = new GeoService(activityRepository, trackpointRepository);
        Activity activity = Activity.builder().id("a1").name("Run & <fast>").build();

        when(activityRepository.findById("a1")).thenReturn(Optional.of(activity));
        when(trackpointRepository.findByActivityIdOrderByTimestampAsc("a1"))
                .thenReturn(List.of(ActivityTrackpoint.builder()
                        .latitude(40.0).longitude(-3.0).distanceMeters(0.0).build()));

        String gpx = service.exportGpx("a1");

        assertThat(gpx).contains("Run &amp; &lt;fast&gt;");
        assertThat(gpx).doesNotContain("<time>");
    }

    @Test
    void exportGeoJsonHandlesNullStartTimeAndDistance() {
        GeoService service = new GeoService(activityRepository, trackpointRepository);
        Activity activity = Activity.builder().id("a1").name("Run").build();

        when(activityRepository.findById("a1")).thenReturn(Optional.of(activity));
        when(trackpointRepository.findByActivityIdOrderByTimestampAsc("a1")).thenReturn(List.of());

        GeoJsonDto dto = service.exportGeoJson("a1");

        assertThat(dto.getFeatures()).hasSize(1);
        assertThat(dto.getFeatures().get(0).getProperties().get("startTime")).isNull();
        assertThat(dto.getFeatures().get(0).getProperties().get("distanceMeters")).isNull();
    }
}
