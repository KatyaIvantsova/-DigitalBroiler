package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.AuditLogEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    List<AuditLogEntry> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    List<AuditLogEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
