package com.running.api.service;

import java.util.List;

/**
 * Fuente de altitudes reales a partir de coordenadas. Las implementaciones deben devolver
 * una lista del mismo tamaño que la entrada, con {@code null} en las posiciones que no se
 * hayan podido resolver.
 */
public interface ElevationProvider {

    List<Double> lookup(List<Coordinate> coordinates);

    record Coordinate(double latitude, double longitude) {
    }
}
