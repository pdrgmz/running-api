package com.running.api.repository;

import com.running.api.model.RunnerZoneProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RunnerZoneProfileRepository extends JpaRepository<RunnerZoneProfile, Long> {

    @EntityGraph(attributePaths = "zones")
    Optional<RunnerZoneProfile> findByUserId(Long userId);
}
