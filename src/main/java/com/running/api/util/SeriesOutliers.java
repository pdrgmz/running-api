package com.running.api.util;

import java.util.Arrays;

/**
 * Detección de valores atípicos en series numéricas con huecos representados por
 * {@link Double#NaN}.
 * <p>
 * Un valor se considera atípico cuando se aleja de la mediana de sus vecinos válidos más
 * de lo permitido. El umbral combina una desviación absoluta mínima (propia de cada
 * métrica) con la dispersión robusta de los vecinos (MAD, <i>median absolute deviation</i>),
 * de forma que una serie muy variable no marque como atípicos los cambios normales.
 * <p>
 * La detección es conservadora: si no hay suficientes vecinos de referencia, el valor se
 * conserva. Nunca modifica la serie recibida.
 */
public final class SeriesOutliers {

    private static final double MAD_TO_SIGMA = 1.4826;
    private static final double SIGMA_MULTIPLIER = 3.0;

    private SeriesOutliers() {
    }

    /**
     * Marca las posiciones cuyo valor se aleja claramente de sus vecinos.
     *
     * @param values       serie con {@link Double#NaN} en los huecos
     * @param window       máximo de vecinos válidos a considerar por cada lado
     * @param minNeighbors mínimo de vecinos válidos necesarios para evaluar un punto
     * @param minDelta     desviación absoluta mínima respecto a la mediana para considerar
     *                     atípico un valor
     * @return array paralelo a {@code values} con {@code true} en las posiciones atípicas
     */
    public static boolean[] detect(double[] values, int window, int minNeighbors, double minDelta) {
        boolean[] outliers = new boolean[values == null ? 0 : values.length];
        if (values == null || values.length == 0) {
            return outliers;
        }

        int effectiveWindow = Math.max(1, window);
        int effectiveMinNeighbors = Math.max(1, minNeighbors);
        double[] neighbors = new double[effectiveWindow * 2];

        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) {
                continue;
            }

            int count = collectNeighbors(values, i, effectiveWindow, neighbors);
            if (count < effectiveMinNeighbors) {
                continue;
            }

            double median = median(neighbors, count);
            double mad = medianAbsoluteDeviation(neighbors, count, median);
            double threshold = Math.max(minDelta, SIGMA_MULTIPLIER * MAD_TO_SIGMA * mad);

            if (Math.abs(values[i] - median) > threshold) {
                outliers[i] = true;
            }
        }

        return outliers;
    }

    private static int collectNeighbors(double[] values, int index, int window, double[] buffer) {
        int count = 0;

        int left = 0;
        for (int i = index - 1; i >= 0 && left < window; i--) {
            if (Double.isFinite(values[i])) {
                buffer[count++] = values[i];
                left++;
            }
        }

        int right = 0;
        for (int i = index + 1; i < values.length && right < window; i++) {
            if (Double.isFinite(values[i])) {
                buffer[count++] = values[i];
                right++;
            }
        }

        return count;
    }

    static double median(double[] values, int count) {
        double[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);

        if (count % 2 == 1) {
            return sorted[count / 2];
        }
        return (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0;
    }

    private static double medianAbsoluteDeviation(double[] values, int count, double median) {
        double[] deviations = new double[count];
        for (int i = 0; i < count; i++) {
            deviations[i] = Math.abs(values[i] - median);
        }
        return median(deviations, count);
    }
}
