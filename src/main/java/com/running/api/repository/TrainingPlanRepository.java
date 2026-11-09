package com.running.api.repository;

import com.running.api.model.PlanStatus;
import com.running.api.model.TrainingPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, String> {

    @EntityGraph(attributePaths = "workouts")
    Optional<TrainingPlan> findByIdAndUserId(String id, Long userId);

    @EntityGraph(attributePaths = "workouts")
    Optional<TrainingPlan> findByUserIdAndRequestId(Long userId, String requestId);

    Optional<TrainingPlan> findFirstByUserIdAndWeekStartOrderByVersionDesc(Long userId, LocalDate weekStart);

    List<TrainingPlan> findByUserIdAndWeekStartOrderByVersionDesc(Long userId, LocalDate weekStart);

    Optional<TrainingPlan> findFirstByUserIdAndWeekStartAndStatusOrderByVersionDesc(
            Long userId, LocalDate weekStart, PlanStatus status);

    Optional<TrainingPlan> findFirstByUserIdAndStatusOrderByWeekStartDesc(Long userId, PlanStatus status);

    Optional<TrainingPlan> findFirstByUserIdAndWeekStartLessThanEqualAndWeekEndGreaterThanEqualAndStatus(
            Long userId, LocalDate dateFrom, LocalDate dateTo, PlanStatus status);

    @EntityGraph(attributePaths = "workouts")
    List<TrainingPlan> findByUserIdAndWeekStartBetweenOrderByWeekStartDesc(
            Long userId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "workouts")
    List<TrainingPlan> findByUserIdOrderByWeekStartDesc(Long userId);

    boolean existsByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
