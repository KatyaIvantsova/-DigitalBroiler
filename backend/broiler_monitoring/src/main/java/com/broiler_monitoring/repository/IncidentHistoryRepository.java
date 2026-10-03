package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.IncidentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentHistoryRepository extends JpaRepository<IncidentHistory, UUID> {

    Optional<IncidentHistory> findFirstByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    List<IncidentHistory> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
