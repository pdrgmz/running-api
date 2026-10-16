package com.running.api.repository;

import com.running.api.model.Activity;
import com.running.api.model.GlobalSummaryStats;
import com.running.api.repository.projection.DailyTrimpProjection;
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
}
