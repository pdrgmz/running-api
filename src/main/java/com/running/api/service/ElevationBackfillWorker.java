package com.running.api.service;

import com.running.api.model.Activity;
import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.ActivityRepository;
import com.running.api.util.AltitudeUtils;
import com.running.api.util.ElevationAccumulator;
import com.running.api.util.FlatAltitudeDetector;
import com.running.api.util.GeoUtils;
import com.running.api.util.SeriesInterpolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Procesa una actividad concreta para el backfill de altitudes.
 * <p>
 * Solo envía al proveedor los trackpoints sin altitud válida o con una altitud constante
 * sospechosa, y que además están separados al menos {@code sampleDistanceMeters} del último
 * punto enviado. El resto se interpola. No extrapola más allá del último punto consultado:
 * así una actividad larga se puede completar en varias ejecuciones del cron sin rellenar el
 * final con una altitud constante.
 */
@Slf4j
@Service
public class ElevationBackfillWorker {

    private final ActivityRepository activityRepository;
    private final ElevationProvider elevationProvider;
    private final double elevationThreshold;

    public ElevationBackfillWorker(ActivityRepository activityRepository,
                                   ElevationProvider elevationProvider,
                                   @Value("${app.elevation.min-threshold-meters:0.1}") double elevationThreshold) {
        this.activityRepository = activityRepository;
        this.elevationProvider = elevationProvider;
        this.elevationThreshold = elevationThreshold;
    }

    /**
     * @return número de coordenadas enviadas al proveedor (consumo de cuota)
     */
    @Transactional
    public int processActivity(String activityId,
                               int maxCalls,
                               double sampleDistanceMeters,
                               Set<Long> affectedUsers) {
        if (maxCalls <= 0) {
            return 0;
        }

        Activity activity = activityRepository.findById(activityId).orElse(null);
        if (activity == null) {
            return 0;
        }

        List<ActivityTrackpoint> points = activity.getTrackpoints();
        if (points == null || points.isEmpty()) {
            return 0;
        }

        // Altitudes constantes sospechosas: se tratan como ausentes para recalcularlas.
        boolean[] flatAltitudes = FlatAltitudeDetector.detect(points);

        List<Integer> sampleIndexes = selectSamples(points, maxCalls, sampleDistanceMeters, flatAltitudes);
        if (sampleIndexes.isEmpty()) {
            return 0;
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
            log.warn("No se pudieron obtener altitudes para la actividad {}: {}", activityId, e.getMessage());
            return sampleIndexes.size();
        }
        if (elevations == null) {
            elevations = List.of();
        }

        double[] altitudes = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            ActivityTrackpoint point = points.get(i);
            altitudes[i] = needsAltitude(point, flatAltitudes[i])
                    ? Double.NaN
                    : point.getAltitudeMeters();
        }

        for (int i = 0; i < sampleIndexes.size() && i < elevations.size(); i++) {
            Double elevation = elevations.get(i);
            if (elevation != null && Double.isFinite(elevation)) {
                altitudes[sampleIndexes.get(i)] = elevation;
            }
        }

        // Solo rellena huecos entre valores válidos; deja el final sin tocar para que el
        // siguiente lote del cron continúe desde ahí.
        SeriesInterpolation.fillMissingInterior(altitudes);

        // No aplica nada más allá del último punto consultado. Si hubiera una altitud
        // válida muy posterior, interpolar hasta ella rellenaría kilómetros de golpe.
        int lastSampleIndex = sampleIndexes.get(sampleIndexes.size() - 1);
        int applied = 0;
        for (int i = 0; i <= lastSampleIndex; i++) {
            ActivityTrackpoint point = points.get(i);
            if (needsAltitude(point, flatAltitudes[i])
                    && Double.isFinite(altitudes[i])) {
                point.setAltitudeMeters(round(altitudes[i], 1));
                point.setElevationCalculated(true);
                applied++;
            }
        }

        if (applied > 0) {
            recalculateElevation(activity, points);
            if (activity.getUserId() != null) {
                affectedUsers.add(activity.getUserId());
            }
        }

        log.debug("Actividad {}: {} coordenadas consultadas, {} altitudes aplicadas",
                activityId, sampleIndexes.size(), applied);
        return sampleIndexes.size();
    }

    private List<Integer> selectSamples(List<ActivityTrackpoint> points,
                                        int maxCalls,
                                        double sampleDistanceMeters,
                                        boolean[] flatAltitudes) {
        List<Integer> samples = new ArrayList<>();
        ActivityTrackpoint lastSample = null;
        double minimumDistance = Math.max(0.0, sampleDistanceMeters);

        for (int i = 0; i < points.size() && samples.size() < maxCalls; i++) {
            ActivityTrackpoint point = points.get(i);
            if (!needsAltitude(point, flatAltitudes[i])
                    || !GeoUtils.hasValidCoordinate(point)) {
                continue;
            }

            if (lastSample == null
                    || GeoUtils.haversineMeters(
                            lastSample.getLatitude(), lastSample.getLongitude(),
                            point.getLatitude(), point.getLongitude()) >= minimumDistance) {
                samples.add(i);
                lastSample = point;
            }
        }

        return samples;
    }

    /**
     * Un punto necesita altitud cuando le falta o cuando su valor es una altitud plana
     * sospechosa que hay que recalcular.
     */
    private static boolean needsAltitude(ActivityTrackpoint point, boolean flat) {
        return flat || AltitudeUtils.isMissingAltitude(point);
    }

    /**
     * Recalcula el desnivel acumulado a partir de las altitudes actuales de la actividad.
     */
    private void recalculateElevation(Activity activity, List<ActivityTrackpoint> points) {
        ElevationAccumulator accumulator = new ElevationAccumulator(elevationThreshold);
        for (ActivityTrackpoint point : points) {
            Double altitude = point.getAltitudeMeters();
            if (altitude != null && Double.isFinite(altitude)) {
                accumulator.add(altitude);
            }
        }
        activity.setElevationGain(round(accumulator.getGain(), 1));
        activity.setElevationLoss(round(accumulator.getLoss(), 1));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
