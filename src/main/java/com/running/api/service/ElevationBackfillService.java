package com.running.api.service;

import com.running.api.repository.ActivityRepository;
import com.running.api.repository.projection.ElevationBackfillProjection;
import com.running.api.util.AltitudeUtils;
import com.running.api.util.FlatAltitudeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cron de completado de altitudes.
 * <p>
 * Presupuesto por defecto: 80 coordenadas cada 15 minutos.
 * <ul>
 *     <li>80 llamadas por ejecución (límite por minuto: 600).</li>
 *     <li>4 ejecuciones/hora = 320 llamadas/hora (límite: 5.000).</li>
 *     <li>96 ejecuciones/día = 7.680 llamadas/día (límite: 10.000).</li>
 *     <li>~230.400 llamadas/mes (límite: 300.000).</li>
 * </ul>
 * Deja margen para importaciones, reintentos y picos puntuales del proveedor.
 * <p>
 * Además de las altitudes ausentes, procesa las actividades con una altitud constante
 * sospechosa (el mismo valor exacto en al menos el 90% de los puntos del dispositivo) para
 * recalcularlas con el modelo digital.
 */
@Slf4j
@Service
public class ElevationBackfillService {

    private final ActivityRepository activityRepository;
    private final ElevationBackfillWorker worker;
    private final FileService fileService;
    private final boolean enabled;
    private final boolean externalEnabled;
    private final int maxCallsPerRun;
    private final int maxActivitiesPerRun;
    private final double sampleDistanceMeters;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ElevationBackfillService(
            ActivityRepository activityRepository,
            ElevationBackfillWorker worker,
            FileService fileService,
            @Value("${app.elevation.backfill.enabled:true}") boolean enabled,
            @Value("${app.elevation.external-enabled:true}") boolean externalEnabled,
            @Value("${app.elevation.backfill.max-calls-per-run:80}") int maxCallsPerRun,
            @Value("${app.elevation.backfill.max-activities-per-run:50}") int maxActivitiesPerRun,
            @Value("${app.elevation.backfill.sample-distance-meters:90}") double sampleDistanceMeters) {
        this.activityRepository = activityRepository;
        this.worker = worker;
        this.fileService = fileService;
        this.enabled = enabled;
        this.externalEnabled = externalEnabled;
        this.maxCallsPerRun = Math.max(1, maxCallsPerRun);
        this.maxActivitiesPerRun = Math.max(1, maxActivitiesPerRun);
        this.sampleDistanceMeters = Math.max(0.0, sampleDistanceMeters);
    }

    @Scheduled(cron = "${app.elevation.backfill.cron:0 */15 * * * *}")
    public void backfillMissingAltitudes() {
        if (!enabled || !externalEnabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("El backfill de elevación anterior sigue en ejecución; se omite esta ejecución");
            return;
        }

        long startedAt = System.currentTimeMillis();
        int remaining = maxCallsPerRun;
        int calls = 0;
        int activities = 0;
        Set<Long> affectedUsers = new HashSet<>();

        try {
            List<ElevationBackfillProjection> candidates = new ArrayList<>();
            candidates.addAll(activityRepository.findActivitiesWithMissingAltitude(
                    AltitudeUtils.ALTITUDE_EPSILON,
                    PageRequest.of(0, maxActivitiesPerRun)));
            candidates.addAll(activityRepository.findActivitiesWithFlatAltitude(
                    AltitudeUtils.ALTITUDE_EPSILON,
                    FlatAltitudeDetector.FLAT_ALTITUDE_PERCENT,
                    FlatAltitudeDetector.MIN_FLAT_ALTITUDE_POINTS,
                    PageRequest.of(0, maxActivitiesPerRun)));

            List<String> activityIds = candidates.stream()
                    .sorted(Comparator.comparing(ElevationBackfillProjection::getStartTime,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(ElevationBackfillProjection::getActivityId)
                    .distinct()
                    .limit(maxActivitiesPerRun)
                    .toList();

            for (String activityId : activityIds) {
                if (remaining <= 0) {
                    break;
                }
                try {
                    int used = worker.processActivity(
                            activityId, remaining, sampleDistanceMeters, affectedUsers);
                    if (used > 0) {
                        remaining -= used;
                        calls += used;
                        activities++;
                    }
                } catch (Exception e) {
                    log.warn("No se pudo completar la altitud de la actividad {}: {}",
                            activityId, e.getMessage());
                }
            }

            for (Long userId : affectedUsers) {
                try {
                    fileService.recalculateAllStats(userId);
                } catch (Exception e) {
                    log.warn("No se pudieron recalcular las estadísticas del usuario {}: {}",
                            userId, e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - startedAt;
            if (activities > 0 || calls > 0) {
                log.info("Backfill de elevación: {} actividades, {} coordenadas, {} restantes, {} ms",
                        activities, calls, remaining, elapsed);
            } else {
                log.debug("Backfill de elevación: sin actividades pendientes, {} ms", elapsed);
            }
        } catch (Exception e) {
            log.error("Error en el backfill de elevación: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}
