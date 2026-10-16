package com.running.api.controller;

import com.running.api.dto.ErrorResponseDto;
import com.running.api.dto.GeoJsonDto;
import com.running.api.security.UserPrincipal;
import com.running.api.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@Tag(name = "Exportación Geoespacial", description = "Exportación de la ruta GPS del usuario autenticado en formatos GeoJSON y GPX 1.1")
public class GeoController {

    private final GeoService geoService;

    @GetMapping(value = "/{id}/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Exportar la ruta en formato GeoJSON FeatureCollection")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento GeoJSON con la ruta"),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<GeoJsonDto> getGeoJson(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        return ResponseEntity.ok(geoService.exportGeoJson(principal.id(), id));
    }

    @GetMapping(value = "/{id}/gpx", produces = "application/gpx+xml")
    @Operation(summary = "Exportar la ruta en formato GPX 1.1 estandarizado",
               description = "Devuelve el XML GPX 1.1 como archivo descargable (Content-Disposition: attachment).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento GPX 1.1",
                    content = @Content(mediaType = "application/gpx+xml", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "404", description = "La actividad no existe o no pertenece al usuario",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<String> getGpx(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "ID de la actividad", example = "2026-09-05T07:00:00Z")
            @PathVariable String id) {
        String gpxXml = geoService.exportGpx(principal.id(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity_" + id + ".gpx\"")
                .body(gpxXml);
    }
}
