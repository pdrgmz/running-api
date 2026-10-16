package com.running.api.repository;

import com.running.api.model.GlobalSummaryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalSummaryStatsRepository extends JpaRepository<GlobalSummaryStats, Long> {

    Optional<GlobalSummaryStats> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
