package com.running.api.repository;

import com.running.api.model.GlobalSummaryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GlobalSummaryStatsRepository extends JpaRepository<GlobalSummaryStats, Long> {
}
