package com.broiler_monitoring.service;

import com.broiler_monitoring.entity.AuditLogEntry;
import com.broiler_monitoring.repository.AuditLogRepository;
import com.broiler_monitoring.security.CurrentActor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Журнал действий пользователей (S2-06): кто и когда изменил инцидент, норму, партию, учёт. */
@Service
public class AuditService {

    public static final String FLOCK = "FLOCK";
    public static final String INCIDENT = "INCIDENT";
    public static final String NORM = "NORM";
    public static final String USER = "USER";
    public static final String SENSOR = "SENSOR";
    public static final String HOUSE = "HOUSE";

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public AuditLogEntry record(String entityType, Object entityId, String action, String summary, Changes changes) {
        CurrentActor actor = CurrentActor.get();
        AuditLogEntry entry = new AuditLogEntry();
        entry.setEntityType(entityType);
        entry.setEntityId(String.valueOf(entityId));
        entry.setAction(action);
        entry.setActorId(actor.id());
        entry.setActorName(actor.name());
        entry.setSummary(summary);
        entry.setChanges(changes == null || changes.isEmpty() ? null : changes.toString());
        return repository.save(entry);
    }

    public AuditLogEntry record(String entityType, Object entityId, String action, String summary) {
        return record(entityType, entityId, action, summary, null);
    }

    public List<AuditLogEntry> find(String entityType, String entityId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        if (entityType != null && entityId != null) {
            return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                    .stream().limit(safeLimit).toList();
        }
        if (entityType != null) {
            return repository.findByEntityTypeOrderByCreatedAtDesc(entityType, PageRequest.of(0, safeLimit));
        }
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit));
    }

    public static Changes changes() {
        return new Changes();
    }

    /** Список изменённых полей «поле: было → стало». Неизменённые поля не попадают. */
    public static final class Changes {
        private final List<String> lines = new ArrayList<>();

        public Changes field(String label, Object before, Object after) {
            if (!Objects.equals(before, after)) {
                lines.add("%s: %s → %s".formatted(label, show(before), show(after)));
            }
            return this;
        }

        public Changes value(String label, Object value) {
            if (value != null) {
                lines.add("%s: %s".formatted(label, show(value)));
            }
            return this;
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }

        @Override
        public String toString() {
            return String.join("\n", lines);
        }

        private static String show(Object value) {
            if (value == null) return "—";
            if (value instanceof Double number && number == Math.rint(number)) {
                return String.valueOf(number.longValue());
            }
            return String.valueOf(value);
        }
    }
}
