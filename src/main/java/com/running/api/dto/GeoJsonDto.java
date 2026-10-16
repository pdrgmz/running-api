package com.running.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Documento GeoJSON (RFC 7946) con la ruta GPS de una actividad")
public class GeoJsonDto {

    @Schema(description = "Tipo de objeto GeoJSON", example = "FeatureCollection")
    private String type;

    @Schema(description = "Lista de features que componen la colección")
    private List<GeoJsonFeature> features;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Feature GeoJSON que representa la ruta de la actividad")
    public static class GeoJsonFeature {

        @Schema(description = "Tipo de objeto GeoJSON", example = "Feature")
        private String type;

        @Schema(description = "Geometría de la ruta (LineString)")
        private Geometry geometry;

        @Schema(description = "Propiedades asociadas a la actividad (nombre, distancia, tipo, etc.)")
        private Map<String, Object> properties;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Geometría GeoJSON de tipo LineString")
    public static class Geometry {

        @Schema(description = "Tipo de geometría", example = "LineString")
        private String type;

        @Schema(description = "Lista de coordenadas [longitud, latitud, altitud]")
        private List<List<Double>> coordinates;
    }
}
