package com.running.api.repository;

import com.running.api.model.Activity;
import com.running.api.model.GlobalSummaryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.running.api.repository.projection.DailyTrimpProjection;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, String> {

    @Query("SELECT MAX(a.maxHeartRate) FROM Activity a")
    Integer findMaxHeartRateGlobal();

    @Query("SELECT new com.running.api.model.GlobalSummaryStats(" +
           "1L, " +
           "COUNT(a), " +
           "COALESCE(SUM(a.distanceMeters), 0.0), " +
           "COALESCE(SUM(a.totalTimeSeconds), 0.0), " +
           "COALESCE(SUM(a.elevationGain), 0.0), " +
           "COALESCE(SUM(a.totalCalories), 0L), " +
           "COALESCE(MAX(a.maxHeartRate), 0)) " +
           "FROM Activity a")
    GlobalSummaryStats calculateAggregatedSummary();

    @Query("SELECT new com.running.api.model.GlobalSummaryStats(" +
           "1L, " +
           "COUNT(a), " +
           "COALESCE(SUM(a.distanceMeters), 0.0), " +
           "COALESCE(SUM(a.totalTimeSeconds), 0.0), " +
           "COALESCE(SUM(a.elevationGain), 0.0), " +
           "COALESCE(SUM(a.totalCalories), 0L), " +
           "COALESCE(MAX(a.maxHeartRate), 0)) " +
           "FROM Activity a WHERE a.startTime >= :start AND a.startTime <= :end")
    GlobalSummaryStats calculateAggregatedSummaryBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Activity> findByDistanceMetersGreaterThanEqual(Double distanceMeters);


    @Query("""
        SELECT 
            CAST(a.startTime AS java.time.LocalDate) AS date, 
            SUM(a.trainingLoad) AS totalTrimp 
        FROM Activity a 
        WHERE CAST(a.startTime AS java.time.LocalDate) BETWEEN :startDate AND :endDate 
        GROUP BY CAST(a.startTime AS java.time.LocalDate) 
        ORDER BY date ASC
    """)
    List<DailyTrimpProjection> findDailyTrimpSum(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate
    );

    /**
     * Agrupa y calcula la distancia total en km redondeada por día en orden descendente para Eddington.
     */
    @Query("""
        SELECT 
            CAST(FLOOR(SUM(a.distanceMeters) / 1000.0) AS int) 
        FROM Activity a 
        GROUP BY CAST(a.startTime AS java.time.LocalDate) 
        ORDER BY 1 DESC
    """)
    List<Integer> findDailyDistancesKmDescending();

    @Query("SELECT MIN(CAST(a.startTime AS java.time.LocalDate)) FROM Activity a")
       Optional<LocalDate> findEarliestActivityDate();
}
