package com.broiler_monitoring.rules;

import com.broiler_monitoring.entity.Incident;
import com.broiler_monitoring.entity.IncidentHistory;
import com.broiler_monitoring.enumerated.IncidentPriority;
import com.broiler_monitoring.enumerated.IncidentSource;
import com.broiler_monitoring.enumerated.IncidentStatus;
import com.broiler_monitoring.repository.IncidentHistoryRepository;
import com.broiler_monitoring.repository.IncidentRepository;
import com.broiler_monitoring.service.AuditService;
import com.broiler_monitoring.service.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Автоматические инциденты движка правил: создание, повышение приоритета, автозакрытие
 * (docs/sprint2/08-rules-engine-spec.md, п. 4–6). Все переходы пишутся в историю инцидента
 * и журнал действий от имени «Система».
 */
@Service
public class IncidentAutomationService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAutomationService.class);
    public static final String SYSTEM_ACTOR = "Система";
    private static final String BACK_TO_NORMAL = "BACK_TO_NORMAL";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final IncidentRepository incidents;
    private final IncidentHistoryRepository history;
    private final AuditService audit;
    private final Clock clock;

    public IncidentAutomationService(IncidentRepository incidents, IncidentHistoryRepository history, AuditService audit, Clock clock) {
        this.incidents = incidents;
        this.history = history;
        this.audit = audit;
        this.clock = clock;
    }

    /** Отклонение подтверждено: создать инцидент или поднять приоритет открытого. Возвращает инцидент. */
    @Transactional
    public Incident raise(Alert alert) {
        IncidentPriority priority = alert.level() == RuleEngine.Level.CRITICAL ? IncidentPriority.CRITICAL : IncidentPriority.HIGH;
        Optional<Incident> open = incidents.findOpenByDedupKey(alert.dedupKey()).stream().findFirst();
        if (open.isPresent()) {
            Incident incident = open.get();
            if (priority == IncidentPriority.CRITICAL && incident.getPriority() != IncidentPriority.CRITICAL) {
                IncidentPriority before = incident.getPriority();
                incident.setPriority(IncidentPriority.CRITICAL);
                incident.setDescription(alert.description());
                incidents.save(incident);
                note(incident, "ESCALATED", "Отклонение усилилось, приоритет повышен до CRITICAL: " + alert.description());
                audit.record(AuditService.INCIDENT, incident.getId(), "ESCALATED", "Инцидент %s: приоритет повышен".formatted(incident.getCode()),
                        AuditService.changes().field("Приоритет", before, IncidentPriority.CRITICAL));
            }
            return incident;
        }
        Incident incident = new Incident();
        incident.setCode(IncidentService.generateIncidentCode());
        incident.setTitle(alert.title());
        incident.setDescription(alert.description());
        incident.setType(alert.type());
        incident.setPriority(priority);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setSource(IncidentSource.SYSTEM);
        incident.setHouse(alert.houseName());
        incident.setZone(alert.zoneName());
        incident.setHouseId(alert.houseId());
        incident.setZoneId(alert.zoneId());
        incident.setFlockId(alert.flockId());
        incident.setSensorId(alert.sensorId());
        incident.setRuleCode(alert.ruleCode());
        incident.setDedupKey(alert.dedupKey());
        Incident saved = incidents.save(incident);
        note(saved, "CREATED", "Создан правилом %s: %s".formatted(alert.ruleCode(), alert.description()));
        audit.record(AuditService.INCIDENT, saved.getId(), "CREATED", "Правило %s создало инцидент %s".formatted(alert.ruleCode(), saved.getCode()),
                AuditService.changes().value("Приоритет", priority).value("Описание", alert.description()));
        log.info("Правило {} создало инцидент {}: {}", alert.ruleCode(), saved.getCode(), alert.description());
        return saved;
    }

    /**
     * Показатель вернулся в норму. Инцидент, который никто не взял (OPEN), закрывается как решённый;
     * инцидент в работе остаётся у человека — в историю пишется только отметка о норме (один раз).
     */
    @Transactional
    public Optional<Incident> clear(String dedupKey, String message) {
        Optional<Incident> open = incidents.findOpenByDedupKey(dedupKey).stream().findFirst();
        if (open.isEmpty()) {
            return Optional.empty();
        }
        Incident incident = open.get();
        if (incident.getStatus() == IncidentStatus.OPEN) {
            incident.setStatus(IncidentStatus.RESOLVED);
            // Время инцидентов хранится в часовом поясе JVM, как createdAt/detectedAt в Incident.prePersist
            incident.setResolvedAt(LocalDateTime.now(clock));
            incident.setDecisionComment(message);
            incidents.save(incident);
            note(incident, "STATUS_CHANGED", "Статус изменён: OPEN → RESOLVED. " + message);
            audit.record(AuditService.INCIDENT, incident.getId(), "AUTO_RESOLVED",
                    "Инцидент %s закрыт автоматически: %s".formatted(incident.getCode(), message));
            return Optional.of(incident);
        }
        boolean alreadyNoted = history.findFirstByIncidentIdOrderByCreatedAtDesc(incident.getId())
                .map(last -> BACK_TO_NORMAL.equals(last.getEventType()))
                .orElse(false);
        if (!alreadyNoted) {
            String time = LocalDateTime.now(clock).format(TIME);
            note(incident, BACK_TO_NORMAL, "%s с %s. Инцидент в работе — закройте его после проверки.".formatted(message, time));
        }
        return Optional.of(incident);
    }

    private void note(Incident incident, String event, String message) {
        history.save(new IncidentHistory(incident.getId(), event, null, SYSTEM_ACTOR, message));
    }
}
