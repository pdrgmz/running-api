package com.running.api.repository.projection;

import java.time.LocalDateTime;

/**
 * Actividad candidata al backfill de altitudes, con su fecha de inicio para ordenar el
 * trabajo de más antigua a más reciente.
 */
public interface ElevationBackfillProjection {

    String getActivityId();

    LocalDateTime getStartTime();
}
