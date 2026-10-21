package com.running.api.util;

import com.running.api.model.ActivityTrackpoint;

/**
 * Utilidades compartidas para decidir si la altitud de un trackpoint es válida.
 * <p>
 * Una altitud nula, no finita o prácticamente 0 sin calcular se considera ausente. Las
 * altitudes calculadas (aunque valgan 0) se conservan: pueden ser un 0 real devuelto por el
 * modelo digital de elevaciones.
 */
public final class AltitudeUtils {

    /**
     * Diferencia mínima para considerar que una altitud es distinta de cero.
     */
    public static final double ALTITUDE_EPSILON = 1e-6;

    private AltitudeUtils() {
    }

    /**
     * @return {@code true} si la altitud es nula, no finita o está prácticamente a 0.
     */
    public static boolean isMissingAltitude(Double altitude) {
        return !isValidAltitude(altitude);
    }

    /**
     * Igual que {@link #isMissingAltitude(Double)}, pero respeta las altitudes ya calculadas:
     * una altitud calculada se considera válida aunque sea 0.
     */
    public static boolean isMissingAltitude(ActivityTrackpoint point) {
        if (point == null) {
            return true;
        }
        Double altitude = point.getAltitudeMeters();
        if (Boolean.TRUE.equals(point.getElevationCalculated())
                && altitude != null && Double.isFinite(altitude)) {
            return false;
        }
        return isMissingAltitude(altitude);
    }

    private static boolean isValidAltitude(Double altitude) {
        return altitude != null && Double.isFinite(altitude) && Math.abs(altitude) > ALTITUDE_EPSILON;
    }
}
