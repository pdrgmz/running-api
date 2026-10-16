package com.running.api.repository;

import com.running.api.model.PersonalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, Long> {

    Optional<PersonalRecord> findByUserIdAndDistanceCategory(Long userId, String distanceCategory);

    List<PersonalRecord> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
