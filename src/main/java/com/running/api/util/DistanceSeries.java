package com.running.api.util;

import com.running.api.model.ActivityTrackpoint;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reconstrucción de la distancia acumulada de una actividad.
 * <p>
 * La distancia que reportan los dispositivos (sobre todo el campo {@code distance} de FIT) puede
 * contener glitches: saltos de varios kilómetros en un segundo, reinicios del contador a cero o
 * pequeñas oscilaciones. Para que las métricas derivadas (récords, splits, desacople) no se
 * corrompan, aquí se recalcula una serie <b>monótona y físicamente plausible</b>: se suman los
 * incrementos entre muestras consecutivas descartando los negativos (reinicios del contador) y los
 * que implicarían superar {@link RunningMetrics#MAX_PLAUSIBLE_SPEED_MPS} (glitches del sensor).
 * <p>
 * La reconstrucción no altera los datos válidos: para un uso normal (velocidades muy inferiores al
 * límite) los incrementos son idénticos a los reportados, por lo que no se pierde precisión.
 */
public final class DistanceSeries {

    private DistanceSeries() {
    }

    /**
     * Reconstruye la distancia acumulada de una serie de muestras ya ordenada por tiempo.
     *
     * @param rawDistances distancia reportada por muestra (mismo tamaño que {@code timeSeconds})
     * @param timeSeconds  tiempo en segundos de cada muestra (creciente)
     * @param count        número de muestras válidas en los arrays
     * @param maxSpeedMps  velocidad máxima plausible
     * @return nueva serie de distancias, monótona y plausible
     */
    public static double[] reconstruct(double[] rawDistances, double[] timeSeconds, int count, double maxSpeedMps) {
        double[] result = new double[count];
        if (count == 0) {
            return result;
        }

        double accumulated = rawDistances[0];
        double previousRaw = rawDistances[0];
        double previousTime = timeSeconds[0];
        result[0] = accumulated;

        for (int i = 1; i < count; i++) {
            double deltaTime = timeSeconds[i] - previousTime;
            double increment = rawDistances[i] - previousRaw;

            if (increment < 0) {
                increment = 0.0; // el contador acumulado no puede retroceder
            }
            double maxIncrement = maxSpeedMps * deltaTime;
            if (increment > maxIncrement) {
                increment = 0.0; // salto imposible: se descarta el incremento corrupto
            }

            accumulated += increment;
            result[i] = accumulated;
            previousRaw = rawDistances[i];
            previousTime = timeSeconds[i];
        }
        return result;
    }

    /**
     * Variante para una lista de puntos de telemetría: devuelve una serie paralela a {@code points}
     * (mismos índices). Las muestras sin distancia o sin timestamp conservan el último valor
     * reconstruido; las anteriores a la primera muestra válida toman el valor de ésta.
     */
    public static double[] reconstructPerPoint(List<ActivityTrackpoint> points, double maxSpeedMps) {
        int n = points.size();
        double[] raw = new double[n];
        double[] time = new double[n];
        int[] indexOf = new int[n];
        int count = 0;

        LocalDateTime base = null;
        for (int i = 0; i < n; i++) {
            ActivityTrackpoint point = points.get(i);
            if (point.getDistanceMeters() == null || point.getTimestamp() == null) {
                continue;
            }
            if (base == null) {
                base = point.getTimestamp();
            }
            raw[count] = point.getDistanceMeters();
            time[count] = Duration.between(base, point.getTimestamp()).toNanos() / 1_000_000_000.0;
            indexOf[count] = i;
            count++;
        }

        double[] rebuilt = reconstruct(raw, time, count, maxSpeedMps);

        double[] result = new double[n];
        double carried = count > 0 ? rebuilt[0] : 0.0;
        int next = 0;
        for (int i = 0; i < n; i++) {
            if (next < count && indexOf[next] == i) {
                carried = rebuilt[next];
                next++;
            }
            result[i] = carried;
        }
        return result;
    }
}
