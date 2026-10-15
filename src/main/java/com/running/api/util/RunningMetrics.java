package com.running.api.util;

/**
 * Estimaciones fisiológicas simplificadas compartidas por los parsers GPX y FIT.
 * Todos los métodos devuelven {@code null} cuando no hay datos suficientes, de forma que el
 * llamador decida si asignar o no el valor.
 */
public final class RunningMetrics {

    private static final double MIN_SPEED_FOR_VO2_MS = 1.0;
    private static final double MIN_VALID_VO2 = 20.0;
    private static final double MAX_VALID_VO2 = 100.0;

    private RunningMetrics() {
    }

    /**
     * Carga de entrenamiento (TRIMP simplificado de Banister):
     * {@code duración(min) * (FC media / FC máxima)}.
     *
     * @return el valor redondeado a 1 decimal, o {@code null} si faltan datos.
     */
    public static Double trainingLoad(double durationMinutes, double avgHeartRate, int maxHeartRate) {
        if (durationMinutes <= 0 || avgHeartRate <= 0 || maxHeartRate <= 0 || maxHeartRate <= avgHeartRate) {
            return null;
        }
        double trimp = durationMinutes * (avgHeartRate / maxHeartRate);
        return Math.round(trimp * 10.0) / 10.0;
    }

    /**
     * VAM (Velocidad Aeróbica Máxima) en km/h a partir de la velocidad máxima y la pendiente media
     * derivada del desnivel positivo sobre la distancia.
     *
     * @return el valor redondeado a 1 decimal, o {@code null} si no hay velocidad válida.
     */
    public static Double vamKmH(double maxSpeedMs, Double distanceMeters, Double elevationGainMeters) {
        if (maxSpeedMs <= 0) {
            return null;
        }
        double avgGradient = 0.0;
        if (distanceMeters != null && distanceMeters > 0
                && elevationGainMeters != null && elevationGainMeters > 0) {
            avgGradient = elevationGainMeters / distanceMeters;
        }
        double vam = (maxSpeedMs * 3.6) * (1 + Math.max(avgGradient, 0) * 0.1);
        return Math.round(vam * 10.0) / 10.0;
    }

    /**
     * VO2Max estimado con la fórmula de Daniels-Gilbert:
     * {@code VO2 = -4.60 + 0.182258*v + 0.000104*v^2} con {@code v} en m/min.
     *
     * @return el valor redondeado a 1 decimal, o {@code null} si queda fuera del rango fisiológico.
     */
    public static Double vo2Max(double maxSpeedMs) {
        if (maxSpeedMs <= MIN_SPEED_FOR_VO2_MS) {
            return null;
        }
        double v = maxSpeedMs * 60.0;
        double vo2 = -4.60 + 0.182258 * v + 0.000104 * v * v;
        if (vo2 <= MIN_VALID_VO2 || vo2 >= MAX_VALID_VO2) {
            return null;
        }
        return Math.round(vo2 * 10.0) / 10.0;
    }

    /**
     * Determina si un texto de deporte corresponde a carrera a pie
     * (running, trail_running, run, carrera, ...).
     */
    public static boolean isRunningSport(String sport) {
        if (sport == null) {
            return false;
        }
        String normalized = sport.toLowerCase();
        return normalized.contains("running") || normalized.contains("run") || normalized.contains("carrera");
    }
}
