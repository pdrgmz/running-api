package com.running.api.repository;

import com.running.api.model.Activity;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.projection.DailyTrimpProjection;
import com.running.api.repository.projection.ElevationBackfillProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, String> {

    Optional<Activity> findByIdAndUserId(String id, Long userId);

    Page<Activity> findByUserId(Long userId, Pageable pageable);

    List<Activity> findByUserId(Long userId);

    @Query("SELECT a.id FROM Activity a WHERE a.userId = :userId ORDER BY a.startTime ASC")
    List<String> findIdsByUserId(@Param("userId") Long userId);

    List<Activity> findByUserIdOrderByStartTimeDesc(Long userId);

    List<Activity> findByUserIdAndDistanceMetersGreaterThanEqual(Long userId, Double distanceMeters);

    List<Activity> findByIdInAndUserId(List<String> ids, Long userId);

    List<Activity> findByUserIdIsNull();

    @Query("SELECT MAX(a.maxHeartRate) FROM Activity a WHERE a.userId = :userId")
    Integer findMaxHeartRateByUserId(@Param("userId") Long userId);

    @Query("SELECT new com.running.api.model.GlobalSummaryStats(" +
           "COUNT(a), " +
           "COALESCE(SUM(a.distanceMeters), 0.0), " +
           "COALESCE(SUM(a.totalTimeSeconds), 0.0), " +
           "COALESCE(SUM(a.elevationGain), 0.0), " +
           "COALESCE(SUM(a.totalCalories), 0L), " +
           "COALESCE(MAX(a.maxHeartRate), 0)) " +
           "FROM Activity a WHERE a.userId = :userId")
    GlobalSummaryStats calculateAggregatedSummary(@Param("userId") Long userId);

    @Query("SELECT new com.running.api.model.GlobalSummaryStats(" +
           "COUNT(a), " +
           "COALESCE(SUM(a.distanceMeters), 0.0), " +
           "COALESCE(SUM(a.totalTimeSeconds), 0.0), " +
           "COALESCE(SUM(a.elevationGain), 0.0), " +
           "COALESCE(SUM(a.totalCalories), 0L), " +
           "COALESCE(MAX(a.maxHeartRate), 0)) " +
           "FROM Activity a WHERE a.userId = :userId AND a.startTime >= :start AND a.startTime <= :end")
    GlobalSummaryStats calculateAggregatedSummaryBetween(@Param("userId") Long userId,
                                                        @Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);

    @Query("""
        SELECT
            CAST(a.startTime AS java.time.LocalDate) AS date,
            COALESCE(SUM(a.trainingLoad), 0.0) AS totalTrimp
        FROM Activity a
        WHERE a.userId = :userId
          AND CAST(a.startTime AS java.time.LocalDate) BETWEEN :startDate AND :endDate
        GROUP BY CAST(a.startTime AS java.time.LocalDate)
        ORDER BY date ASC
    """)
    List<DailyTrimpProjection> findDailyTrimpSum(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT
            CAST(FLOOR(COALESCE(SUM(a.distanceMeters), 0.0) / 1000.0) AS int)
        FROM Activity a
        WHERE a.userId = :userId
        GROUP BY CAST(a.startTime AS java.time.LocalDate)
        ORDER BY 1 DESC
    """)
    List<Integer> findDailyDistancesKmDescending(@Param("userId") Long userId);

    @Query("SELECT MIN(CAST(a.startTime AS java.time.LocalDate)) FROM Activity a WHERE a.userId = :userId")
    Optional<LocalDate> findEarliestActivityDate(@Param("userId") Long userId);

    /**
     * Actividades con al menos un trackpoint sin altitud utilizable y con coordenadas válidas.
     * Se ordenan de más antigua a más reciente para que el cron no deje datos históricos atrás.
     */
    @Query("""
        SELECT a.id AS activityId, a.startTime AS startTime
        FROM Activity a
        WHERE EXISTS (
            SELECT 1 FROM ActivityTrackpoint t
            WHERE t.activity.id = a.id
              AND (t.altitudeMeters IS NULL
                   OR (ABS(t.altitudeMeters) < :epsilon
                       AND (t.elevationCalculated IS NULL OR t.elevationCalculated = false)))
              AND t.latitude IS NOT NULL
              AND t.longitude IS NOT NULL
        )
        ORDER BY a.startTime ASC
        """)
    List<ElevationBackfillProjection> findActivitiesWithMissingAltitude(@Param("epsilon") double epsilon,
                                                                        Pageable pageable);

    /**
     * Actividades con una altitud constante sospechosa: un mismo valor exacto cubre al menos
     * el porcentaje indicado de los trackpoints con altitud registrada por el dispositivo.
     * Las altitudes ya calculadas (interpolación o modelo digital) no cuentan, de modo que
     * una actividad deja de estar pendiente en cuanto se recalcula. Se ordenan de más antigua
     * a más reciente.
     */
    @Query("""
        SELECT t.activity.id AS activityId, MIN(t.activity.startTime) AS startTime
        FROM ActivityTrackpoint t
        WHERE t.altitudeMeters IS NOT NULL
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
        ORDER BY MIN(t.activity.startTime) ASC
        """)
    List<ElevationBackfillProjection> findActivitiesWithFlatAltitude(@Param("epsilon") double epsilon,
                                                                     @Param("flatPercent") int flatPercent,
                                                                     @Param("minPoints") int minPoints,
                                                                     Pageable pageable);
}
