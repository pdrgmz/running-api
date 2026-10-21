package com.running.api.repository;

import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.projection.ElevationCoverageProjection;
import com.running.api.repository.projection.FlatElevationProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityTrackpointRepository extends JpaRepository<ActivityTrackpoint, Long> {
    List<ActivityTrackpoint> findByActivityIdOrderByTimestampAsc(String activityId);

    /**
     * Cuenta los trackpoints del usuario y cuántos tienen una altitud válida. Se usa el
     * mismo criterio que el cron: una altitud nula o 0 se considera pendiente.
     */
    @Query("""
        SELECT COUNT(t) AS totalTrackpoints,
               COUNT(CASE WHEN t.altitudeMeters IS NOT NULL
                           AND (ABS(t.altitudeMeters) > :epsilon OR t.elevationCalculated = true)
                          THEN 1 END) AS trackpointsWithElevation
        FROM ActivityTrackpoint t
        WHERE t.activity.userId = :userId
        """)
    ElevationCoverageProjection getElevationCoverage(@Param("userId") Long userId,
                                                     @Param("epsilon") double epsilon);

    /**
     * Cuenta, por actividad, los trackpoints que comparten una altitud constante sospechosa:
     * un mismo valor exacto registrado por el dispositivo cubre al menos el porcentaje
     * indicado de los trackpoints con altitud del dispositivo. Coincide con el criterio del
     * backfill: estas altitudes se consideran pendientes de recalcular.
     */
    @Query("""
        SELECT t.activity.id AS activityId, COUNT(t) AS occurrences
        FROM ActivityTrackpoint t
        WHERE t.activity.userId = :userId
          AND t.altitudeMeters IS NOT NULL
          AND ABS(t.altitudeMeters) > :epsilon
          AND (t.elevationCalculated IS NULL OR t.elevationCalculated = false)
        GROUP BY t.activity.id, t.altitudeMeters
        HAVING COUNT(t) >= :minPoints
           AND COUNT(t) * 100 >= :flatPercent * (
               SELECT COUNT(t2) FROM ActivityTrackpoint t2
               WHERE t2.activity.id = t.activity.id
                 AND t2.altitudeMeters IS NOT NULL
                 AND ABS(t2.altitudeMeters) > :epsilon
                 AND (t2.elevationCalculated IS NULL OR t2.elevationCalculated = false))
        """)
    List<FlatElevationProjection> findFlatElevationPoints(@Param("userId") Long userId,
                                                          @Param("epsilon") double epsilon,
                                                          @Param("flatPercent") int flatPercent,
                                                          @Param("minPoints") int minPoints);
}
