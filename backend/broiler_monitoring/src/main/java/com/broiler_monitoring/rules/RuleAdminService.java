package com.broiler_monitoring.rules;

import com.broiler_monitoring.dto.norms.RuleUpdateRequest;
import com.broiler_monitoring.entity.Rule;
import com.broiler_monitoring.repository.RuleRepository;
import com.broiler_monitoring.service.AuditService;
import com.broiler_monitoring.service.StructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Правка порогов правил технологом: применяется со следующего цикла движка, пишется в журнал. */
@Service
public class RuleAdminService {

    public static final String RULE = "RULE";

    private final RuleRepository rules;
    private final AuditService audit;

    public RuleAdminService(RuleRepository rules, AuditService audit) {
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional
    public Rule update(String code, RuleUpdateRequest request) {
        Rule rule = rules.findById(code).orElseThrow(() -> StructureService.notFound("Правило", code));
        AuditService.Changes changes = AuditService.changes()
                .field("Минут до предупреждения", rule.getWarnMinutes(), request.warnMinutes())
                .field("Критичная дельта", rule.getCriticalDelta(), request.criticalDelta())
                .field("Минут до критичного", rule.getCriticalMinutes(), request.criticalMinutes())
                .field("Минут до закрытия", rule.getClearMinutes(), request.clearMinutes())
                .field("Включено", rule.isEnabled(), request.enabled());
        rule.setWarnMinutes(request.warnMinutes());
        rule.setCriticalDelta(request.criticalDelta());
        rule.setCriticalMinutes(request.criticalMinutes());
        rule.setClearMinutes(request.clearMinutes());
        rule.setEnabled(request.enabled());
        Rule saved = rules.save(rule);
        if (!changes.isEmpty()) {
            audit.record(RULE, code, "UPDATED", "Изменено правило %s".formatted(rule.getName()), changes);
        }
        return saved;
    }
}
