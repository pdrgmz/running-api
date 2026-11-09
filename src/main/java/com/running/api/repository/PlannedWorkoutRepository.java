package com.running.api.repository;

import com.running.api.model.PlannedWorkout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlannedWorkoutRepository extends JpaRepository<PlannedWorkout, Long> {

    List<PlannedWorkout> findByPlan_IdOrderByWorkoutDateAsc(String planId);
}
