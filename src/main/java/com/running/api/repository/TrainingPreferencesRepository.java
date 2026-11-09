package com.running.api.repository;

import com.running.api.model.TrainingPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingPreferencesRepository extends JpaRepository<TrainingPreferences, Long> {

    Optional<TrainingPreferences> findByUserId(Long userId);

    List<TrainingPreferences> findByEnabledTrue();
}
