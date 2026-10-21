package com.running.api.repository.projection;

/**
 * Trackpoints de una actividad que comparten una altitud constante sospechosa
 * (al menos el 90% del mismo valor exacto entre las altitudes del dispositivo).
 */
public interface FlatElevationProjection {

    String getActivityId();

    Long getOccurrences();
}
