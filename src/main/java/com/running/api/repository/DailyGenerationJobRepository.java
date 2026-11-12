package com.running.api.repository;

import com.running.api.model.DailyGenerationJob;
import com.running.api.model.TrainingJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyGenerationJobRepository extends JpaRepository<DailyGenerationJob, String> {

    Optional<DailyGenerationJob> findByIdAndUserId(String id, Long userId);

    List<DailyGenerationJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<DailyGenerationJob> findFirstByUserIdAndRecommendationDateAndStatusInOrderByCreatedAtDesc(
            Long userId, LocalDate recommendationDate, Collection<TrainingJobStatus> statuses);

    boolean existsByUserIdAndRecommendationDateAndStatusIn(
            Long userId, LocalDate recommendationDate, Collection<TrainingJobStatus> statuses);

    boolean existsByUserIdAndRecommendationDate(Long userId, LocalDate recommendationDate);

    List<DailyGenerationJob> findByUserIdAndStatus(Long userId, TrainingJobStatus status);

    List<DailyGenerationJob> findByStatusInAndUpdatedAtBefore(
            Collection<TrainingJobStatus> statuses, LocalDateTime threshold);

    List<DailyGenerationJob> findByUserIdAndRecommendationId(Long userId, String recommendationId);
}
