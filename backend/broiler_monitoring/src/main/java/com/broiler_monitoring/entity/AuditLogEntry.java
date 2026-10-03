package com.broiler_monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Запись журнала действий: кто, когда и что изменил (S2-06). */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntry {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String entityType;

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, length = 32)
    private String action;

    private UUID actorId;

    private String actorName;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    /** Изменённые поля в виде «поле: было → стало», по одному на строку. */
    @Column(columnDefinition = "text")
    private String changes;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
