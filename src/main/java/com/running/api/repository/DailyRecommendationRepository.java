package com.running.api.repository;

import com.running.api.model.DailyRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyRecommendationRepository extends JpaRepository<DailyRecommendation, String> {

    Optional<DailyRecommendation> findByIdAndUserId(String id, Long userId);

    Optional<DailyRecommendation> findByUserIdAndRecommendationDate(Long userId, LocalDate recommendationDate);

    boolean existsByUserIdAndRecommendationDate(Long userId, LocalDate recommendationDate);

    List<DailyRecommendation> findByUserIdAndRecommendationDateBetweenOrderByRecommendationDateDesc(
            Long userId, LocalDate from, LocalDate to);

    List<DailyRecommendation> findTop30ByUserIdOrderByRecommendationDateDesc(Long userId);
}
