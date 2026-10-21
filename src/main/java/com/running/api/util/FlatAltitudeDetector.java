package com.running.api.util;

import com.running.api.model.ActivityTrackpoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detección de altitudes "planas" de origen sospechoso.
 * <p>
 * Algunos dispositivos registran una altitud constante cuando su sensor de altitud falla
 * (por ejemplo, siempre el mismo valor heredado del inicio de la grabación). Si un mismo
 * valor exacto aparece en al menos el {@value #FLAT_ALTITUDE_PERCENT}% de los trackpoints
 * con altitud del dispositivo, esas posiciones se consideran pendientes de recalcular con
 * el modelo digital de elevaciones o por interpolación.
 * <p>
 * Las altitudes ya calculadas ({@code elevationCalculated = true}) no cuentan: una vez
 * recalculadas, la actividad deja de estar pendiente aunque el resultado sea llano.
 */
public final class FlatAltitudeDetector {

    /**
     * Porcentaje mínimo de puntos con exactamente la misma altitud para considerarla plana.
     */
    public static final int FLAT_ALTITUDE_PERCENT = 90;

    /**
     * Mínimo de puntos con el mismo valor exacto para aplicar la detección; evita falsos
     * positivos en actividades cortas.
     */
    public static final int MIN_FLAT_ALTITUDE_POINTS = 10;

    /**
     * Mismo umbral que {@code TrackpointCompletionService.ALTITUDE_EPSILON}: una altitud
     * cercana a 0 sin calcular se trata como ausente, no como altitud plana.
     */
    private static final double ALTITUDE_EPSILON = 1e-6;

    private FlatAltitudeDetector() {
    }

    /**
     * Marca las posiciones que pertenecen a una altitud plana sospechosa. La detección solo
     * tiene en cuenta las altitudes registradas por el dispositivo (no las calculadas).
     *
     * @param points trackpoints de la actividad en orden
     * @return array paralelo a {@code points} con {@code true} en las posiciones planas
     */
    public static boolean[] detect(List<ActivityTrackpoint> points) {
        boolean[] flat = new boolean[points == null ? 0 : points.size()];
        if (points == null || points.isEmpty()) {
            return flat;
        }

        Map<Double, Integer> counts = new HashMap<>();
        int devicePoints = 0;

        for (ActivityTrackpoint point : points) {
            if (!isDeviceAltitude(point)) {
                continue;
            }
            counts.merge(point.getAltitudeMeters(), 1, Integer::sum);
            devicePoints++;
        }

        Double dominant = null;
        int dominantCount = 0;
        for (Map.Entry<Double, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > dominantCount) {
                dominant = entry.getKey();
                dominantCount = entry.getValue();
            }
        }

        if (dominantCount < MIN_FLAT_ALTITUDE_POINTS
                || (long) dominantCount * 100 < (long) FLAT_ALTITUDE_PERCENT * devicePoints) {
            return flat;
        }

        for (int i = 0; i < points.size(); i++) {
            ActivityTrackpoint point = points.get(i);
            if (isDeviceAltitude(point)
                    && Double.compare(point.getAltitudeMeters(), dominant) == 0) {
                flat[i] = true;
            }
        }

        return flat;
    }

    /**
     * Indica si la altitud de un punto proviene del dispositivo (no fue calculada) y es
     * utilizable: finita y distinta de 0 dentro del épsilon.
     */
    private static boolean isDeviceAltitude(ActivityTrackpoint point) {
        if (point == null || Boolean.TRUE.equals(point.getElevationCalculated())) {
            return false;
        }
        Double altitude = point.getAltitudeMeters();
        return altitude != null && Double.isFinite(altitude) && Math.abs(altitude) > ALTITUDE_EPSILON;
    }
}
