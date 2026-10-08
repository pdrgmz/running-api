package com.running.api.repository;

import com.running.api.model.Activity;
import com.running.api.model.GlobalSummaryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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
}
