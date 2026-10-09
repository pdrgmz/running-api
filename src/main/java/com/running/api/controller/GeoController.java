package com.running.api.controller;

import com.running.api.dto.GeoJsonDto;
import com.running.api.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@Tag(name = "Exportación Geoespacial", description = "Endpoints para exportar la ruta GPS en formatos GeoJSON y GPX 1.1")
public class GeoController {

    private final GeoService geoService;

    @GetMapping(value = "/{id}/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Exportar ruta en formato GeoJSON FeatureCollection")
    public ResponseEntity<GeoJsonDto> getGeoJson(@PathVariable String id) {
        return ResponseEntity.ok(geoService.exportGeoJson(id));
    }

    @GetMapping(value = "/{id}/gpx", produces = "application/gpx+xml")
    @Operation(summary = "Exportar ruta en formato GPX 1.1 estandarizado")
    public ResponseEntity<String> getGpx(@PathVariable String id) {
        String gpxXml = geoService.exportGpx(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity_" + id + ".gpx\"")
                .body(gpxXml);
    }
}
