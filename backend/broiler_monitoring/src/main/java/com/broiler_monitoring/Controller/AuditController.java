package com.broiler_monitoring.Controller;

import com.broiler_monitoring.entity.AuditLogEntry;
import com.broiler_monitoring.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Журнал действий", description = "Кто и когда изменил партию, учёт, инцидент, норму, пользователя")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Записи журнала, новые сверху",
            description = "entityType: FLOCK, INCIDENT, NORM, USER, SENSOR, HOUSE. Технолог, руководитель, администратор.")
    public List<AuditLogEntry> find(@RequestParam(required = false) String entityType,
                                    @RequestParam(required = false) String entityId,
                                    @RequestParam(defaultValue = "100") int limit) {
        return service.find(entityType, entityId, limit);
    }
}
