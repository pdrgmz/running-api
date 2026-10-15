package com.running.api.util;

/**
 * Acumula desnivel positivo (D+) y negativo (D-) a partir de una secuencia de altitudes,
 * aplicando histéresis para filtrar el ruido del altímetro: la altitud de referencia solo
 * avanza cuando la diferencia supera {@code noiseThresholdMeters}.
 * <p>
 * Las muestras sin dato ({@link #NO_DATA}) se ignoran.
 */
public class ElevationAccumulator {

    /** Valor centinela usado por los parsers para indicar que no hay altitud. */
    public static final double NO_DATA = -999.0;

    private final double noiseThresholdMeters;
    private double previousAltitude = NO_DATA;
    private double gain;
    private double loss;

    public ElevationAccumulator(double noiseThresholdMeters) {
        this.noiseThresholdMeters = noiseThresholdMeters;
    }

    /**
     * Añade una muestra de altitud en metros. Si {@code altitudeMeters} es {@link #NO_DATA}
     * la muestra se descarta.
     */
    public void add(double altitudeMeters) {
        if (altitudeMeters == NO_DATA) {
            return;
        }
        if (previousAltitude == NO_DATA) {
            previousAltitude = altitudeMeters;
            return;
        }
        double diff = altitudeMeters - previousAltitude;
        if (Math.abs(diff) > noiseThresholdMeters) {
            if (diff > 0) {
                gain += diff;
            } else {
                loss += Math.abs(diff);
            }
            previousAltitude = altitudeMeters;
        }
    }

    public double getGain() {
        return gain;
    }

    public double getLoss() {
        return loss;
    }
}
