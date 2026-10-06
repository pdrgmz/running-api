package com.running.api.repository;

import com.running.api.model.ActivityTrackpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityTrackpointRepository extends JpaRepository<ActivityTrackpoint, Long> {
    List<ActivityTrackpoint> findByActivityIdOrderByTimestampAsc(String activityId);
}
