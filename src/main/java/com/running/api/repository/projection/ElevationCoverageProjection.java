package com.running.api.repository.projection;

/**
 * Conteo agregado de trackpoints con y sin altitud para un usuario.
 */
public interface ElevationCoverageProjection {

    Long getTotalTrackpoints();

    Long getTrackpointsWithElevation();
}
