package com.running.api.repository;

import com.running.api.model.ActivityTrackpoint;
import com.running.api.repository.projection.ElevationCoverageProjection;
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
}
