package com.running.api.util;

import java.util.Collection;
import java.util.List;

/**
 * Estimaciones fisiológicas simplificadas compartidas por los parsers GPX y FIT.
 * Todos los métodos devuelven {@code null} cuando no hay datos suficientes, de forma que el
 * llamador decida si asignar o no el valor.
 */
public final class RunningMetrics {

    /**
     * Velocidad máxima físicamente plausible (m/s) para una carrera a pie. Cualquier incremento de
     * distancia que implique superarla se considera un glitch del sensor (p. ej. saltos de varios
     * kilómetros en un segundo). 15 m/s ≈ 54 km/h, muy por encima de cualquier marca real, por lo
     * que no altera la telemetría válida. Se usa de forma uniforme en parsers y analítica.
     */
    public static final double MAX_PLAUSIBLE_SPEED_MPS = 15.0;

    /**
     * Rango fisiológico aceptado para la frecuencia cardíaca. Los valores fuera de este
     * intervalo se consideran lecturas corruptas del sensor (p. ej. 0 o 255) y se descartan
     * antes de interpolar.
     */
    public static final int MIN_PLAUSIBLE_HEART_RATE_BPM = 30;
    public static final int MAX_PLAUSIBLE_HEART_RATE_BPM = 240;

    /**
     * Rango fisiológico aceptado para el pico de una sesión. Es más estrecho que el rango
     * muestra a muestra: un pico de 30 bpm no es una FC máxima válida, y por encima de 230
     * lo habitual es que sea un artefacto del sensor.
     */
    public static final int MIN_PLAUSIBLE_MAX_HEART_RATE_BPM = 120;
    public static final int MAX_PLAUSIBLE_MAX_HEART_RATE_BPM = 230;

    /**
     * Número mínimo de sesiones con FC para aplicar el filtro de picos. Con muy pocas
     * sesiones la distribución es demasiado plana para distinguir un máximo real de un
     * artefacto, así que se devuelve el máximo absoluto.
     */
    private static final int MIN_SESSIONS_FOR_MAX_HR_FILTER = 8;

    /** Factor del cerco de Tukey ({@code Q3 + factor * IQR}) usado para descartar picos. */
    private static final double MAX_HEART_RATE_OUTLIER_IQR_FACTOR = 1.5;

    /**
     * Rango fisiológico aceptado para la cadencia de carrera. Los valores fuera de este
     * intervalo se consideran lecturas corruptas del sensor y se descartan.
     */
    public static final int MIN_PLAUSIBLE_CADENCE_SPM = 30;
    public static final int MAX_PLAUSIBLE_CADENCE_SPM = 240;

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
     * @return {@code true} si la frecuencia cardíaca está dentro del rango fisiológico.
     */
    public static boolean isPlausibleHeartRate(Integer heartRate) {
        return heartRate != null
                && heartRate >= MIN_PLAUSIBLE_HEART_RATE_BPM
                && heartRate <= MAX_PLAUSIBLE_HEART_RATE_BPM;
    }

    /**
     * FC máxima de referencia robusta frente a picos aislados.
     *
     * <p>Se parte de los picos de FC de cada sesión (no de las muestras sueltas) y se descartan
     * los que quedan por encima del cerco de Tukey {@code Q3 + 1.5 * IQR}. Así, una lectura
     * puntual sobreestimada por un sensor óptico (por ejemplo, 200 bpm cuando el resto de
     * sesiones se mueven entre 170 y 193) no desplaza la referencia fisiológica de todo el
     * historial. Los picos plausibles y repetidos se conservan.
     *
     * <p>Con menos de {@value #MIN_SESSIONS_FOR_MAX_HR_FILTER} sesiones no se filtra: no hay
     * evidencia suficiente para calificar un valor como atípico.
     *
     * @param sessionMaxima picos de FC de las sesiones del atleta
     * @return el mayor pico no atípico, o {@code 0} si no hay ningún valor fisiológicamente válido
     */
    public static int robustMaxHeartRate(Collection<Integer> sessionMaxima) {
        if (sessionMaxima == null || sessionMaxima.isEmpty()) {
            return 0;
        }

        List<Integer> values = sessionMaxima.stream()
                .filter(hr -> hr != null
                        && hr >= MIN_PLAUSIBLE_MAX_HEART_RATE_BPM
                        && hr <= MAX_PLAUSIBLE_MAX_HEART_RATE_BPM)
                .sorted()
                .toList();

        if (values.isEmpty()) {
            return 0;
        }

        int absoluteMax = values.get(values.size() - 1);
        if (values.size() < MIN_SESSIONS_FOR_MAX_HR_FILTER) {
            return absoluteMax;
        }

        double q1 = percentile(values, 0.25);
        double q3 = percentile(values, 0.75);
        double upperFence = q3 + MAX_HEART_RATE_OUTLIER_IQR_FACTOR * (q3 - q1);

        int best = 0;
        for (int value : values) {
            if (value <= upperFence) {
                best = value;
            }
        }
        return best > 0 ? best : absoluteMax;
    }

    private static double percentile(List<Integer> sortedValues, double percentile) {
        double rank = percentile * (sortedValues.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double fraction = rank - lower;
        return sortedValues.get(lower) + fraction * (sortedValues.get(upper) - sortedValues.get(lower));
    }

    /**
     * @return {@code true} si la cadencia está dentro del rango fisiológico.
     */
    public static boolean isPlausibleCadence(Integer cadence) {
        return cadence != null
                && cadence >= MIN_PLAUSIBLE_CADENCE_SPM
                && cadence <= MAX_PLAUSIBLE_CADENCE_SPM;
    }

    /**
     * @return {@code true} si la velocidad es positiva y no supera el máximo plausible.
     */
    public static boolean isPlausibleSpeed(Double speed) {
        return speed != null && Double.isFinite(speed)
                && speed > 0.0 && speed <= MAX_PLAUSIBLE_SPEED_MPS;
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
