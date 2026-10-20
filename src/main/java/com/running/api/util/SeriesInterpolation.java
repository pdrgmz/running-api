package com.running.api.util;

/**
 * Relleno de series numéricas con huecos representados por {@link Double#NaN}.
 * <p>
 * Los valores interiores se interpolan linealmente entre el vecino válido anterior y el
 * siguiente. Los huecos iniciales y finales se rellenan con el valor válido más cercano.
 */
public final class SeriesInterpolation {

    private SeriesInterpolation() {
    }

    /**
     * Completa in-place todos los huecos de la serie.
     *
     * @return número de posiciones rellenadas; {@code 0} si no hay ningún valor válido.
     */
    public static int fillMissing(double[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }

        int firstValid = -1;
        for (int i = 0; i < values.length; i++) {
            if (isValid(values[i])) {
                firstValid = i;
                break;
            }
        }
        if (firstValid < 0) {
            return 0;
        }

        int filled = 0;

        // Huecos iniciales: se usa el primer valor válido.
        for (int i = 0; i < firstValid; i++) {
            values[i] = values[firstValid];
            filled++;
        }

        // Huecos interiores: interpolación lineal entre los dos valores válidos que los rodean.
        int previousValid = firstValid;
        for (int i = firstValid + 1; i < values.length; i++) {
            if (!isValid(values[i])) {
                continue;
            }
            int gapSize = i - previousValid - 1;
            if (gapSize > 0) {
                double start = values[previousValid];
                double delta = values[i] - start;
                for (int j = previousValid + 1; j < i; j++) {
                    values[j] = start + delta * (j - previousValid) / (double) (i - previousValid);
                    filled++;
                }
            }
            previousValid = i;
        }

        // Huecos finales: se usa el último valor válido.
        for (int i = previousValid + 1; i < values.length; i++) {
            values[i] = values[previousValid];
            filled++;
        }

        return filled;
    }

    /**
     * Completa solo los huecos interiores entre el primer y el último valor válido.
     * Los huecos iniciales y finales se dejan como {@link Double#NaN} para no extrapolar
     * altitudes fuera del rango realmente consultado.
     *
     * @return número de posiciones rellenadas; {@code 0} si no hay dos valores válidos.
     */
    public static int fillMissingInterior(double[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }

        int firstValid = -1;
        int lastValid = -1;
        for (int i = 0; i < values.length; i++) {
            if (isValid(values[i])) {
                if (firstValid < 0) {
                    firstValid = i;
                }
                lastValid = i;
            }
        }
        if (firstValid < 0 || lastValid <= firstValid) {
            return 0;
        }

        int filled = 0;
        int previousValid = firstValid;
        for (int i = firstValid + 1; i <= lastValid; i++) {
            if (!isValid(values[i])) {
                continue;
            }
            int gapSize = i - previousValid - 1;
            if (gapSize > 0) {
                double start = values[previousValid];
                double delta = values[i] - start;
                for (int j = previousValid + 1; j < i; j++) {
                    values[j] = start + delta * (j - previousValid) / (double) (i - previousValid);
                    filled++;
                }
            }
            previousValid = i;
        }
        return filled;
    }

    public static boolean isValid(double value) {
        return Double.isFinite(value);
    }
}
