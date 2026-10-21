package com.running.api.service;

import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.util.AltitudeUtils;
import com.running.api.util.DistanceSeries;
import com.running.api.util.ElevationAccumulator;
import com.running.api.util.GeoUtils;
import com.running.api.util.RunningMetrics;
import com.running.api.util.SeriesInterpolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Completa los huecos de telemetría de una actividad durante la importación.
 * <p>
 * Rellena latitud, longitud, altitud, frecuencia cardíaca, cadencia y velocidad interpolando
 * con los puntos vecinos, descartando antes las lecturas fuera de rango fisiológico, y ajusta
 * la distancia acumulada. No consulta proveedores externos: la altitud que no se puede
 * interpolar entre los puntos existentes la completa el cron de backfill.
 * <p>
 * La función no persiste nada: modifica la {@link Activity} recibida, que debe estar gestionada
 * por JPA para que los cambios se guarden al hacer commit.
 */
@Slf4j
@Service
public class TrackpointCompletionService {

    private final double elevationThreshold;

    public TrackpointCompletionService(
            @Value("${app.elevation.min-threshold-meters:0.1}") double elevationThreshold) {
        this.elevationThreshold = elevationThreshold;
    }

    /**
     * Completa la actividad después de importarla. Se llama dentro de la transacción del
     * llamador, después de guardar la actividad.
     */
    public void completeAfterImport(Activity activity) {
        if (activity == null) {
            return;
        }

        List<ActivityTrackpoint> points = activity.getTrackpoints();
        if (points == null || points.isEmpty()) {
            return;
        }

        int latitudeFilled = fillCoordinate(points,
                ActivityTrackpoint::getLatitude,
                ActivityTrackpoint::setLatitude,
                value -> !GeoUtils.isValidLatitude(value));

        int longitudeFilled = fillCoordinate(points,
                ActivityTrackpoint::getLongitude,
                ActivityTrackpoint::setLongitude,
                value -> !GeoUtils.isValidLongitude(value));

        int altitudeFilled = interpolateAltitudes(points);

        DistanceAdjustment distanceAdjustment = adjustDistances(points);

        int heartRateRemoved = sanitizeIntegerField(points,
                ActivityTrackpoint::getHeartRate,
                ActivityTrackpoint::setHeartRate,
                RunningMetrics::isPlausibleHeartRate);

        int cadenceRemoved = sanitizeIntegerField(points,
                ActivityTrackpoint::getCadence,
                ActivityTrackpoint::setCadence,
                RunningMetrics::isPlausibleCadence);

        int speedRemoved = sanitizeSpeed(points);

        int heartRateFilled = fillIntegerField(points,
                ActivityTrackpoint::getHeartRate,
                ActivityTrackpoint::setHeartRate,
                value -> !RunningMetrics.isPlausibleHeartRate(value));

        int cadenceFilled = fillIntegerField(points,
                ActivityTrackpoint::getCadence,
                ActivityTrackpoint::setCadence,
                value -> !RunningMetrics.isPlausibleCadence(value));

        int speedFilled = fillSpeed(points);

        updateActivitySummary(activity, points);

        int totalFilled = latitudeFilled + longitudeFilled + altitudeFilled
                + heartRateFilled + cadenceFilled + speedFilled;
        int totalRemoved = heartRateRemoved + cadenceRemoved + speedRemoved;
        log.debug("Actividad {}: telemetría completada ({} valores rellenados, {} descartados, {} distancias ajustadas)",
                activity.getId(), totalFilled, totalRemoved, distanceAdjustment.adjustedPoints());
    }

    private int fillCoordinate(List<ActivityTrackpoint> points,
                               Function<ActivityTrackpoint, Double> getter,
                               BiConsumer<ActivityTrackpoint, Double> setter,
                               Predicate<Double> isMissing) {
        double[] values = new double[points.size()];
        boolean[] wasMissing = new boolean[points.size()];
        int missing = 0;

        for (int i = 0; i < points.size(); i++) {
            Double value = getter.apply(points.get(i));
            if (isMissing.test(value)) {
                values[i] = Double.NaN;
                wasMissing[i] = true;
                missing++;
            } else {
                values[i] = value;
            }
        }

        if (missing == 0 || SeriesInterpolation.fillMissing(values) == 0) {
            return 0;
        }

        int applied = 0;
        for (int i = 0; i < points.size(); i++) {
            if (wasMissing[i] && SeriesInterpolation.isValid(values[i])) {
                setter.accept(points.get(i), values[i]);
                applied++;
            }
        }
        return applied;
    }

    private int fillIntegerField(List<ActivityTrackpoint> points,
                                 Function<ActivityTrackpoint, Integer> getter,
                                 BiConsumer<ActivityTrackpoint, Integer> setter,
                                 Predicate<Integer> isMissing) {
        double[] values = new double[points.size()];
        boolean[] wasMissing = new boolean[points.size()];
        int missing = 0;

        for (int i = 0; i < points.size(); i++) {
            Integer value = getter.apply(points.get(i));
            if (isMissing.test(value)) {
                values[i] = Double.NaN;
                wasMissing[i] = true;
                missing++;
            } else {
                values[i] = value;
            }
        }

        if (missing == 0 || SeriesInterpolation.fillMissing(values) == 0) {
            return 0;
        }

        int applied = 0;
        for (int i = 0; i < points.size(); i++) {
            if (wasMissing[i] && SeriesInterpolation.isValid(values[i])) {
                setter.accept(points.get(i), (int) Math.round(values[i]));
                applied++;
            }
        }
        return applied;
    }

    /**
     * Descarta los valores de una serie entera que no estén dentro del rango fisiológico,
     * dejándolos como huecos para que se interpolen con sus vecinos.
     *
     * @return número de valores descartados
     */
    private int sanitizeIntegerField(List<ActivityTrackpoint> points,
                                     Function<ActivityTrackpoint, Integer> getter,
                                     BiConsumer<ActivityTrackpoint, Integer> setter,
                                     Predicate<Integer> isPlausible) {
        int removed = 0;
        for (ActivityTrackpoint point : points) {
            Integer value = getter.apply(point);
            if (value != null && !isPlausible.test(value)) {
                setter.accept(point, null);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Ajusta la distancia acumulada de los trackpoints descartando retrocesos y saltos que
     * implicarían una velocidad superior a la máxima plausible. Los valores corregidos se
     * escriben en los propios puntos.
     *
     * @return número de puntos ajustados y metros descartados
     */
    private DistanceAdjustment adjustDistances(List<ActivityTrackpoint> points) {
        int size = points.size();
        double[] original = new double[size];
        boolean[] hasOriginal = new boolean[size];
        boolean[] invalidOriginal = new boolean[size];
        boolean hasAnyValid = false;
        int invalidCount = 0;

        for (int i = 0; i < size; i++) {
            Double distance = points.get(i).getDistanceMeters();
            if (distance == null) {
                continue;
            }
            if (!Double.isFinite(distance) || distance < 0.0) {
                invalidOriginal[i] = true;
                invalidCount++;
                points.get(i).setDistanceMeters(null);
            } else {
                original[i] = distance;
                hasOriginal[i] = true;
                hasAnyValid = true;
            }
        }

        if (!hasAnyValid) {
            return new DistanceAdjustment(invalidCount, 0.0);
        }

        double[] adjusted = DistanceSeries.reconstructPerPoint(
                points, RunningMetrics.MAX_PLAUSIBLE_SPEED_MPS);

        int adjustedPoints = 0;
        double discardedMeters = 0.0;

        for (int i = 0; i < size; i++) {
            boolean needsUpdate = invalidOriginal[i] || !hasOriginal[i]
                    || Math.abs(original[i] - adjusted[i]) > 1e-6;
            if (needsUpdate) {
                adjustedPoints++;
                points.get(i).setDistanceMeters(adjusted[i]);
            }

            if (i > 0 && hasOriginal[i] && hasOriginal[i - 1]) {
                double originalIncrement = original[i] - original[i - 1];
                double adjustedIncrement = adjusted[i] - adjusted[i - 1];
                if (originalIncrement > adjustedIncrement) {
                    discardedMeters += originalIncrement - adjustedIncrement;
                }
            }
        }

        return new DistanceAdjustment(adjustedPoints, discardedMeters);
    }

    /**
     * Descarta las velocidades no finitas, no positivas o superiores al máximo plausible.
     *
     * @return número de valores descartados
     */
    private int sanitizeSpeed(List<ActivityTrackpoint> points) {
        int removed = 0;
        for (ActivityTrackpoint point : points) {
            Double speed = point.getSpeed();
            if (speed != null && !RunningMetrics.isPlausibleSpeed(speed)) {
                point.setSpeed(null);
                removed++;
            }
        }
        return removed;
    }

    private int fillSpeed(List<ActivityTrackpoint> points) {
        return fillSpeed(points, value -> !RunningMetrics.isPlausibleSpeed(value));
    }

    private int fillSpeed(List<ActivityTrackpoint> points, Predicate<Double> isMissing) {
        int filled = fillDoubleField(points,
                ActivityTrackpoint::getSpeed,
                ActivityTrackpoint::setSpeed,
                isMissing,
                3);

        if (filled > 0) {
            return filled;
        }

        // Sin ninguna velocidad válida: se deriva de la distancia acumulada y el tiempo.
        return deriveSpeedsFromDistance(points, isMissing);
    }

    private int fillDoubleField(List<ActivityTrackpoint> points,
                                Function<ActivityTrackpoint, Double> getter,
                                BiConsumer<ActivityTrackpoint, Double> setter,
                                Predicate<Double> isMissing,
                                int decimals) {
        double[] values = new double[points.size()];
        boolean[] wasMissing = new boolean[points.size()];
        int missing = 0;

        for (int i = 0; i < points.size(); i++) {
            Double value = getter.apply(points.get(i));
            if (isMissing.test(value)) {
                values[i] = Double.NaN;
                wasMissing[i] = true;
                missing++;
            } else {
                values[i] = value;
            }
        }

        if (missing == 0 || SeriesInterpolation.fillMissing(values) == 0) {
            return 0;
        }

        int applied = 0;
        for (int i = 0; i < points.size(); i++) {
            if (wasMissing[i] && SeriesInterpolation.isValid(values[i])) {
                setter.accept(points.get(i), round(values[i], decimals));
                applied++;
            }
        }
        return applied;
    }

    private int deriveSpeedsFromDistance(List<ActivityTrackpoint> points, Predicate<Double> isMissing) {
        int filled = 0;

        for (int i = 1; i < points.size(); i++) {
            ActivityTrackpoint current = points.get(i);
            ActivityTrackpoint previous = points.get(i - 1);

            if (!isMissing.test(current.getSpeed())
                    || current.getDistanceMeters() == null || previous.getDistanceMeters() == null
                    || current.getTimestamp() == null || previous.getTimestamp() == null) {
                continue;
            }

            long seconds = Duration.between(previous.getTimestamp(), current.getTimestamp()).getSeconds();
            if (seconds <= 0) {
                continue;
            }

            double speed = (current.getDistanceMeters() - previous.getDistanceMeters()) / seconds;
            if (RunningMetrics.isPlausibleSpeed(speed)) {
                current.setSpeed(round(speed, 3));
                filled++;
            }
        }

        // El primer punto no tiene segmento anterior: se copia la velocidad del siguiente.
        if (points.size() > 1) {
            ActivityTrackpoint first = points.get(0);
            Double nextSpeed = points.get(1).getSpeed();
            if (isMissing.test(first.getSpeed()) && !isMissing.test(nextSpeed)) {
                first.setSpeed(nextSpeed);
                filled++;
            }
        }

        return filled;
    }

    private int interpolateAltitudes(List<ActivityTrackpoint> points) {
        double[] values = new double[points.size()];
        boolean[] wasMissing = new boolean[points.size()];
        int missing = 0;

        for (int i = 0; i < points.size(); i++) {
            ActivityTrackpoint point = points.get(i);
            if (AltitudeUtils.isMissingAltitude(point)) {
                values[i] = Double.NaN;
                wasMissing[i] = true;
                missing++;
            } else {
                values[i] = point.getAltitudeMeters();
            }
        }

        if (missing == 0 || SeriesInterpolation.fillMissing(values) == 0) {
            return 0;
        }

        int applied = 0;
        for (int i = 0; i < points.size(); i++) {
            if (wasMissing[i] && SeriesInterpolation.isValid(values[i])) {
                ActivityTrackpoint point = points.get(i);
                point.setAltitudeMeters(round(values[i], 1));
                point.setElevationCalculated(true);
                applied++;
            }
        }
        return applied;
    }

    private void updateActivitySummary(Activity activity, List<ActivityTrackpoint> points) {
        updateTelemetrySummary(activity, points);
        updateElevationMetrics(activity, points);
    }

    private void updateTelemetrySummary(Activity activity, List<ActivityTrackpoint> points) {
        int heartRateCount = 0;
        int heartRateSum = 0;
        int heartRateMax = 0;
        int cadenceCount = 0;
        int cadenceSum = 0;
        int cadenceMax = 0;
        double maxSpeed = 0.0;

        for (ActivityTrackpoint point : points) {
            Integer heartRate = point.getHeartRate();
            if (RunningMetrics.isPlausibleHeartRate(heartRate)) {
                heartRateCount++;
                heartRateSum += heartRate;
                heartRateMax = Math.max(heartRateMax, heartRate);
            }

            Integer cadence = point.getCadence();
            if (RunningMetrics.isPlausibleCadence(cadence)) {
                cadenceCount++;
                cadenceSum += cadence;
                cadenceMax = Math.max(cadenceMax, cadence);
            }

            Double speed = point.getSpeed();
            if (RunningMetrics.isPlausibleSpeed(speed)) {
                maxSpeed = Math.max(maxSpeed, speed);
            }
        }

        if (heartRateCount > 0) {
            if (activity.getAvgHeartRate() == null
                    || !RunningMetrics.isPlausibleHeartRate(activity.getAvgHeartRate())) {
                activity.setAvgHeartRate((int) Math.round((double) heartRateSum / heartRateCount));
            }
            if (activity.getMaxHeartRate() == null
                    || !RunningMetrics.isPlausibleHeartRate(activity.getMaxHeartRate())
                    || heartRateMax > activity.getMaxHeartRate()) {
                activity.setMaxHeartRate(heartRateMax);
            }
        } else {
            if (activity.getAvgHeartRate() != null
                    && !RunningMetrics.isPlausibleHeartRate(activity.getAvgHeartRate())) {
                activity.setAvgHeartRate(null);
            }
            if (activity.getMaxHeartRate() != null
                    && !RunningMetrics.isPlausibleHeartRate(activity.getMaxHeartRate())) {
                activity.setMaxHeartRate(null);
            }
        }

        if (cadenceCount > 0) {
            if (activity.getAvgCadence() == null
                    || !RunningMetrics.isPlausibleCadence(activity.getAvgCadence())) {
                activity.setAvgCadence((int) Math.round((double) cadenceSum / cadenceCount));
            }
            if (activity.getMaxCadence() == null
                    || !RunningMetrics.isPlausibleCadence(activity.getMaxCadence())
                    || cadenceMax > activity.getMaxCadence()) {
                activity.setMaxCadence(cadenceMax);
            }
        } else {
            if (activity.getAvgCadence() != null
                    && !RunningMetrics.isPlausibleCadence(activity.getAvgCadence())) {
                activity.setAvgCadence(null);
            }
            if (activity.getMaxCadence() != null
                    && !RunningMetrics.isPlausibleCadence(activity.getMaxCadence())) {
                activity.setMaxCadence(null);
            }
        }

        if (maxSpeed > 0.0) {
            double roundedMaxSpeed = round(maxSpeed, 2);
            if (activity.getMaxSpeed() == null
                    || !RunningMetrics.isPlausibleSpeed(activity.getMaxSpeed())
                    || roundedMaxSpeed > activity.getMaxSpeed()) {
                activity.setMaxSpeed(roundedMaxSpeed);
            }
        } else if (activity.getMaxSpeed() != null
                && !RunningMetrics.isPlausibleSpeed(activity.getMaxSpeed())) {
            activity.setMaxSpeed(null);
        }

        if (activity.getAvgSpeed() == null
                && activity.getDistanceMeters() != null
                && activity.getTotalTimeSeconds() != null
                && activity.getTotalTimeSeconds() > 0.0) {
            activity.setAvgSpeed(activity.getDistanceMeters() / activity.getTotalTimeSeconds());
        }

        if (activity.getAvgPaceMinPerKm() == null
                && activity.getAvgSpeed() != null && activity.getAvgSpeed() > 0.0) {
            activity.setAvgPaceMinPerKm(round(1000.0 / (activity.getAvgSpeed() * 60.0), 2));
        }

        if (activity.getMaxPaceMinPerKm() == null
                && activity.getMaxSpeed() != null && activity.getMaxSpeed() > 0.0) {
            activity.setMaxPaceMinPerKm(round(1000.0 / (activity.getMaxSpeed() * 60.0), 2));
        }
    }

    /**
     * Recalcula el desnivel acumulado si la actividad no traía D+ o era 0.
     */
    private void updateElevationMetrics(Activity activity, List<ActivityTrackpoint> points) {
        boolean hasAltitude = false;
        for (ActivityTrackpoint point : points) {
            Double altitude = point.getAltitudeMeters();
            if (altitude != null && Double.isFinite(altitude)) {
                hasAltitude = true;
                break;
            }
        }

        boolean elevationMissing = activity.getElevationGain() == null
                || activity.getElevationGain() == 0.0;
        if (!hasAltitude || !elevationMissing) {
            return;
        }

        ElevationAccumulator accumulator = new ElevationAccumulator(elevationThreshold);
        for (ActivityTrackpoint point : points) {
            if (point.getAltitudeMeters() != null && Double.isFinite(point.getAltitudeMeters())) {
                accumulator.add(point.getAltitudeMeters());
            }
        }
        activity.setElevationGain(round(accumulator.getGain(), 1));
        activity.setElevationLoss(round(accumulator.getLoss(), 1));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private record DistanceAdjustment(int adjustedPoints, double discardedMeters) {
    }
}
