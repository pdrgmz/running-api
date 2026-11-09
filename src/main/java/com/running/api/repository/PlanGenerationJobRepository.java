package com.running.api.repository;

import com.running.api.model.PlanGenerationJob;
import com.running.api.model.TrainingJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlanGenerationJobRepository extends JpaRepository<PlanGenerationJob, String> {

    Optional<PlanGenerationJob> findByIdAndUserId(String id, Long userId);

    List<PlanGenerationJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PlanGenerationJob> findFirstByUserIdAndWeekStartAndStatusInOrderByCreatedAtDesc(
            Long userId, LocalDate weekStart, Collection<TrainingJobStatus> statuses);

    boolean existsByUserIdAndWeekStartAndStatusIn(
            Long userId, LocalDate weekStart, Collection<TrainingJobStatus> statuses);

    List<PlanGenerationJob> findByUserIdAndStatus(Long userId, TrainingJobStatus status);

    List<PlanGenerationJob> findByUserIdAndPlanIdIn(Long userId, Collection<String> planIds);

    List<PlanGenerationJob> findByStatusInAndUpdatedAtBefore(
            Collection<TrainingJobStatus> statuses, LocalDateTime updatedBefore);
}
