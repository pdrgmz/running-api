package com.running.api.service;

import com.running.api.dto.TrackpointCompletionResultDto;
import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.util.ElevationAccumulator;
import com.running.api.util.RunningMetrics;
import com.running.api.util.SeriesInterpolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Completa los datos que falten en los trackpoints de una actividad:
 * <ul>
 *     <li>Latitud y longitud: interpolación lineal con los puntos GPS válidos vecinos.</li>
 *     <li>Altitud, frecuencia cardíaca, cadencia y velocidad: interpolación con los valores
 *     válidos vecinos (los extremos usan el vecino más cercano).</li>
 *     <li>Si toda la altitud es 0 o nula, se consulta un modelo digital de elevaciones
 *     (Open-Meteo) usando las coordenadas GPS y se interpolan las altitudes restantes.</li>
 * </ul>
 * La función no persiste nada: modifica la {@link Activity} recibida para que pueda usarse
 * tanto durante la importación como sobre una entidad ya cargada.
 */
@Slf4j
@Service
public class TrackpointCompletionService {

    public static final double ALTITUDE_EPSILON = 1e-6;
    private static final double MAX_PLAUSIBLE_SPEED_MPS = RunningMetrics.MAX_PLAUSIBLE_SPEED_MPS;

    private final ElevationProvider elevationProvider;
    private final int elevationSampleEvery;
    private final double elevationThreshold;
    private final boolean externalElevationOnImport;

    @Autowired
    public TrackpointCompletionService(
            ElevationProvider elevationProvider,
            @Value("${app.elevation.sample-every:30}") int elevationSampleEvery,
            @Value("${app.elevation.min-threshold-meters:0.1}") double elevationThreshold,
            @Value("${app.elevation.external-on-import:false}") boolean externalElevationOnImport) {
        this.elevationProvider = elevationProvider;
        this.elevationSampleEvery = Math.max(1, elevationSampleEvery);
        this.elevationThreshold = elevationThreshold;
        this.externalElevationOnImport = externalElevationOnImport;
    }

    TrackpointCompletionService(ElevationProvider elevationProvider,
                                int elevationSampleEvery,
                                double elevationThreshold) {
        this(elevationProvider, elevationSampleEvery, elevationThreshold, true);
    }

    /**
     * Completa los huecos de todos los trackpoints de la actividad, incluida la altitud
     * externa si es necesario. Es el método usado por el endpoint manual.
     *
     * @param activity actividad a completar (se modifica in-place)
     * @return resumen de cuántos valores se completaron y el origen de la altitud
     */
    public TrackpointCompletionResultDto complete(Activity activity) {
        return complete(activity, true);
    }

    /**
     * Completa la actividad después de importarla. La altitud externa solo se consulta si
     * {@code app.elevation.external-on-import=true}; por defecto se deja para el cron.
     */
    public TrackpointCompletionResultDto completeAfterImport(Activity activity) {
        return complete(activity, externalElevationOnImport);
    }

    private TrackpointCompletionResultDto complete(Activity activity, boolean allowExternalElevation) {
        if (activity == null) {
            return TrackpointCompletionResultDto.builder()
                    .altitudeSource("none")
                    .message("La actividad es nula")
                    .build();
        }

        List<ActivityTrackpoint> points = activity.getTrackpoints();
        if (points == null || points.isEmpty()) {
            return TrackpointCompletionResultDto.builder()
                    .activityId(activity.getId())
                    .totalTrackpoints(0)
                    .altitudeSource("none")
                    .message("La actividad no tiene trackpoints")
                    .build();
        }

        int latitudeFilled = fillCoordinate(points,
                ActivityTrackpoint::getLatitude,
                ActivityTrackpoint::setLatitude,
                value -> value == null || !Double.isFinite(value) || value < -90.0 || value > 90.0);

        int longitudeFilled = fillCoordinate(points,
                ActivityTrackpoint::getLongitude,
                ActivityTrackpoint::setLongitude,
                value -> value == null || !Double.isFinite(value) || value < -180.0 || value > 180.0);

        AltitudeFillResult altitudeResult = fillAltitudes(points, allowExternalElevation);
        int heartRateFilled = fillIntegerField(points,
                ActivityTrackpoint::getHeartRate,
                ActivityTrackpoint::setHeartRate,
                value -> value == null || value <= 0);

        int cadenceFilled = fillIntegerField(points,
                ActivityTrackpoint::getCadence,
                ActivityTrackpoint::setCadence,
                value -> value == null || value <= 0);

        int speedFilled = fillSpeed(points);

        updateActivitySummary(activity, points, altitudeResult.external());

        String altitudeSource = altitudeResult.external()
                ? "open-meteo"
                : altitudeResult.filled() > 0 ? "trackpoints" : "none";

        int totalFilled = latitudeFilled + longitudeFilled + altitudeResult.filled()
                + heartRateFilled + cadenceFilled + speedFilled;

        String message = totalFilled == 0
                ? "No había datos que completar"
                : altitudeResult.external()
                        ? "Trackpoints completados; altitud obtenida del modelo digital de elevaciones"
                        : "Trackpoints completados con datos de los puntos vecinos";

        return TrackpointCompletionResultDto.builder()
                .activityId(activity.getId())
                .totalTrackpoints(points.size())
                .latitudeFilled(latitudeFilled)
                .longitudeFilled(longitudeFilled)
                .altitudeFilled(altitudeResult.filled())
                .heartRateFilled(heartRateFilled)
                .cadenceFilled(cadenceFilled)
                .speedFilled(speedFilled)
                .externalElevationUsed(altitudeResult.external())
                .altitudeSource(altitudeSource)
                .message(message)
                .build();
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

    private int fillSpeed(List<ActivityTrackpoint> points) {
        int filled = fillDoubleField(points,
                ActivityTrackpoint::getSpeed,
                ActivityTrackpoint::setSpeed,
                value -> value == null || !Double.isFinite(value)
                        || value <= 0.0 || value > MAX_PLAUSIBLE_SPEED_MPS);

        if (filled > 0) {
            for (ActivityTrackpoint point : points) {
                if (point.getSpeed() != null && point.getSpeed() > 0.0) {
                    point.setSpeed(round(point.getSpeed(), 3));
                }
            }
            return filled;
        }

        // Sin ninguna velocidad válida: se deriva de la distancia acumulada y el tiempo.
        return deriveSpeedsFromDistance(points);
    }

    private int fillDoubleField(List<ActivityTrackpoint> points,
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

    private int deriveSpeedsFromDistance(List<ActivityTrackpoint> points) {
        int filled = 0;

        for (int i = 1; i < points.size(); i++) {
            ActivityTrackpoint current = points.get(i);
            ActivityTrackpoint previous = points.get(i - 1);

            if (!isMissingSpeed(current.getSpeed())
                    || current.getDistanceMeters() == null || previous.getDistanceMeters() == null
                    || current.getTimestamp() == null || previous.getTimestamp() == null) {
                continue;
            }

            long seconds = Duration.between(previous.getTimestamp(), current.getTimestamp()).getSeconds();
            if (seconds <= 0) {
                continue;
            }

            double speed = (current.getDistanceMeters() - previous.getDistanceMeters()) / seconds;
            if (speed > 0.0 && speed <= MAX_PLAUSIBLE_SPEED_MPS) {
                current.setSpeed(round(speed, 3));
                filled++;
            }
        }

        // El primer punto no tiene segmento anterior: se copia la velocidad del siguiente.
        if (points.size() > 1) {
            ActivityTrackpoint first = points.get(0);
            Double nextSpeed = points.get(1).getSpeed();
            if (isMissingSpeed(first.getSpeed()) && !isMissingSpeed(nextSpeed)) {
                first.setSpeed(nextSpeed);
                filled++;
            }
        }

        return filled;
    }

    private AltitudeFillResult fillAltitudes(List<ActivityTrackpoint> points, boolean allowExternalElevation) {
        boolean hasValidAltitude = false;
        for (ActivityTrackpoint point : points) {
            if (!isMissingAltitude(point)) {
                hasValidAltitude = true;
                break;
            }
        }

        if (allowExternalElevation && !hasValidAltitude && hasCoordinates(points)) {
            AltitudeFillResult externalResult = fillAltitudesFromProvider(points);
            if (externalResult.external()) {
                return externalResult;
            }
        }

        return new AltitudeFillResult(interpolateAltitudes(points), false);
    }

    private int interpolateAltitudes(List<ActivityTrackpoint> points) {
        double[] values = new double[points.size()];
        boolean[] wasMissing = new boolean[points.size()];
        int missing = 0;

        for (int i = 0; i < points.size(); i++) {
            ActivityTrackpoint point = points.get(i);
            Double altitude = point.getAltitudeMeters();
            if (!isMissingAltitude(point)) {
                values[i] = altitude;
            } else {
                values[i] = Double.NaN;
                wasMissing[i] = true;
                missing++;
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

    private AltitudeFillResult fillAltitudesFromProvider(List<ActivityTrackpoint> points) {
        List<Integer> sampleIndexes = elevationSampleIndexes(points);
        if (sampleIndexes.isEmpty()) {
            return new AltitudeFillResult(0, false);
        }

        List<ElevationProvider.Coordinate> coordinates = new ArrayList<>(sampleIndexes.size());
        for (int index : sampleIndexes) {
            ActivityTrackpoint point = points.get(index);
            coordinates.add(new ElevationProvider.Coordinate(point.getLatitude(), point.getLongitude()));
        }

        List<Double> elevations;
        try {
            elevations = elevationProvider.lookup(coordinates);
        } catch (Exception e) {
            log.warn("No se pudieron obtener altitudes externas: {}", e.getMessage());
            return new AltitudeFillResult(0, false);
        }

        if (elevations == null || elevations.isEmpty()) {
            return new AltitudeFillResult(0, false);
        }

        double[] values = new double[points.size()];
        Arrays.fill(values, Double.NaN);

        int validSamples = 0;
        for (int i = 0; i < sampleIndexes.size() && i < elevations.size(); i++) {
            Double elevation = elevations.get(i);
            if (elevation != null && Double.isFinite(elevation)) {
                values[sampleIndexes.get(i)] = elevation;
                validSamples++;
            }
        }

        if (validSamples == 0) {
            return new AltitudeFillResult(0, false);
        }

        if (validSamples < sampleIndexes.size()) {
            log.warn("Solo se obtuvieron {} de {} altitudes externas; se interpolan los huecos",
                    validSamples, sampleIndexes.size());
        }

        SeriesInterpolation.fillMissing(values);

        int applied = 0;
        for (int i = 0; i < points.size(); i++) {
            ActivityTrackpoint point = points.get(i);
            if (isMissingAltitude(point) && Double.isFinite(values[i])) {
                point.setAltitudeMeters(round(values[i], 1));
                point.setElevationCalculated(true);
                applied++;
            }
        }
        return new AltitudeFillResult(applied, true);
    }

    private List<Integer> elevationSampleIndexes(List<ActivityTrackpoint> points) {
        List<Integer> validIndexes = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (hasValidCoordinate(points.get(i))) {
                validIndexes.add(i);
            }
        }
        if (validIndexes.isEmpty()) {
            return List.of();
        }

        List<Integer> samples = new ArrayList<>();
        for (int i = 0; i < validIndexes.size(); i += elevationSampleEvery) {
            samples.add(validIndexes.get(i));
        }

        int lastIndex = validIndexes.get(validIndexes.size() - 1);
        if (samples.get(samples.size() - 1) != lastIndex) {
            samples.add(lastIndex);
        }
        return samples;
    }

    private void updateActivitySummary(Activity activity, List<ActivityTrackpoint> points,
                                       boolean externalElevationUsed) {
        int heartRateCount = 0;
        int heartRateSum = 0;
        int heartRateMax = 0;
        int cadenceCount = 0;
        int cadenceSum = 0;
        int cadenceMax = 0;
        double maxSpeed = 0.0;

        for (ActivityTrackpoint point : points) {
            Integer heartRate = point.getHeartRate();
            if (heartRate != null && heartRate > 0) {
                heartRateCount++;
                heartRateSum += heartRate;
                heartRateMax = Math.max(heartRateMax, heartRate);
            }

            Integer cadence = point.getCadence();
            if (cadence != null && cadence > 0) {
                cadenceCount++;
                cadenceSum += cadence;
                cadenceMax = Math.max(cadenceMax, cadence);
            }

            Double speed = point.getSpeed();
            if (speed != null && speed > 0.0 && speed <= MAX_PLAUSIBLE_SPEED_MPS) {
                maxSpeed = Math.max(maxSpeed, speed);
            }
        }

        if (heartRateCount > 0) {
            activity.setAvgHeartRate((int) Math.round((double) heartRateSum / heartRateCount));
            if (activity.getMaxHeartRate() == null || heartRateMax > activity.getMaxHeartRate()) {
                activity.setMaxHeartRate(heartRateMax);
            }
        }

        if (cadenceCount > 0) {
            activity.setAvgCadence((int) Math.round((double) cadenceSum / cadenceCount));
            if (activity.getMaxCadence() == null || cadenceMax > activity.getMaxCadence()) {
                activity.setMaxCadence(cadenceMax);
            }
        }

        if (maxSpeed > 0.0) {
            double roundedMaxSpeed = round(maxSpeed, 2);
            if (activity.getMaxSpeed() == null || roundedMaxSpeed > activity.getMaxSpeed()) {
                activity.setMaxSpeed(roundedMaxSpeed);
            }
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

        updateElevationMetrics(activity, points, externalElevationUsed);
    }

    /**
     * Recalcula el desnivel acumulado a partir de las altitudes actuales de la actividad.
     * Lo usa el cron después de completar altitudes externas.
     */
    public void recalculateElevation(Activity activity) {
        if (activity == null) {
            return;
        }
        List<ActivityTrackpoint> points = activity.getTrackpoints();
        if (points == null || points.isEmpty()) {
            return;
        }
        updateElevationMetrics(activity, points, true);
    }

    private void updateElevationMetrics(Activity activity, List<ActivityTrackpoint> points, boolean force) {
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
        if (!hasAltitude || (!force && !elevationMissing)) {
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

    private static boolean hasCoordinates(List<ActivityTrackpoint> points) {
        for (ActivityTrackpoint point : points) {
            if (hasValidCoordinate(point)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasValidCoordinate(ActivityTrackpoint point) {
        return isValidLatitude(point.getLatitude()) && isValidLongitude(point.getLongitude());
    }

    private static boolean isValidLatitude(Double latitude) {
        return latitude != null && Double.isFinite(latitude) && latitude >= -90.0 && latitude <= 90.0;
    }

    private static boolean isValidLongitude(Double longitude) {
        return longitude != null && Double.isFinite(longitude) && longitude >= -180.0 && longitude <= 180.0;
    }

    public static boolean isMissingAltitude(Double altitude) {
        return !isValidAltitude(altitude);
    }

    /**
     * Una altitud calculada (aunque sea 0) se considera válida y no se vuelve a consultar.
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

    private static boolean isMissingSpeed(Double speed) {
        return speed == null || !Double.isFinite(speed) || speed <= 0.0
                || speed > MAX_PLAUSIBLE_SPEED_MPS;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private record AltitudeFillResult(int filled, boolean external) {
    }
}
