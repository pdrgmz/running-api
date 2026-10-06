package com.running.api.repository;

import com.running.api.model.Activity;
import com.running.api.model.GlobalSummaryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, String> {

    @Query("SELECT MAX(a.maxHeartRate) FROM Activity a")
    Integer findMaxHeartRateGlobal();

    @Query("SELECT new GlobalSummaryStats(" +
           "1L, " +
           "COUNT(a), " +
           "COALESCE(SUM(a.distanceMeters), 0.0), " +
           "COALESCE(SUM(a.totalTimeSeconds), 0.0), " +
           "COALESCE(SUM(a.elevationGain), 0.0), " +
           "COALESCE(SUM(a.totalCalories), 0L), " +
           "COALESCE(MAX(a.maxHeartRate), 0)) " +
           "FROM Activity a")
    GlobalSummaryStats calculateAggregatedSummary();

    List<Activity> findByDistanceMetersGreaterThanEqual(Double distanceMeters);
}
