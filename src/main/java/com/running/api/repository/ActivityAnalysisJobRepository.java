package com.running.api.repository;

import com.running.api.model.ActivityAnalysisJob;
import com.running.api.model.TrainingJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityAnalysisJobRepository extends JpaRepository<ActivityAnalysisJob, String> {

    Optional<ActivityAnalysisJob> findByIdAndUserId(String id, Long userId);

    Optional<ActivityAnalysisJob> findByIdAndUserIdAndActivityId(String id, Long userId, String activityId);

    List<ActivityAnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndActivityIdAndStatusIn(
            Long userId, String activityId, Collection<TrainingJobStatus> statuses);

    List<ActivityAnalysisJob> findByUserIdAndStatus(Long userId, TrainingJobStatus status);

    List<ActivityAnalysisJob> findByStatusInAndUpdatedAtBefore(
            Collection<TrainingJobStatus> statuses, LocalDateTime threshold);
}
