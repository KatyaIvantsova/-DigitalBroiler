package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.DailyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyRecordRepository extends JpaRepository<DailyRecord, UUID> {

    List<DailyRecord> findByFlockIdOrderByRecordDateAsc(UUID flockId);

    Optional<DailyRecord> findByFlockIdAndRecordDate(UUID flockId, LocalDate recordDate);
}
