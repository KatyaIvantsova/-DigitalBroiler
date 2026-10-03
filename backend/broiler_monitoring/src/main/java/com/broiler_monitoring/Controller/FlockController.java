package com.broiler_monitoring.Controller;

import com.broiler_monitoring.dto.production.CloseFlockRequest;
import com.broiler_monitoring.dto.production.DailyRecordRequest;
import com.broiler_monitoring.dto.production.DailyRecordResponse;
import com.broiler_monitoring.dto.production.FlockRequest;
import com.broiler_monitoring.dto.production.FlockResponse;
import com.broiler_monitoring.dto.production.WeighingRequest;
import com.broiler_monitoring.dto.production.WeighingResponse;
import com.broiler_monitoring.entity.AuditLogEntry;
import com.broiler_monitoring.enumerated.FlockStatus;
import com.broiler_monitoring.service.AuditService;
import com.broiler_monitoring.service.FlockJournalService;
import com.broiler_monitoring.service.FlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flocks")
@Tag(name = "Партии", description = "Посадка, закрытие, ежедневный учёт и взвешивания")
public class FlockController {

    private final FlockService flocks;
    private final FlockJournalService journal;
    private final AuditService audit;

    public FlockController(FlockService flocks, FlockJournalService journal, AuditService audit) {
        this.flocks = flocks;
        this.journal = journal;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Партии", description = "Фильтры: птичник и статус. Оператор видит партии только своих птичников.")
    public List<FlockResponse> find(@RequestParam(required = false) UUID houseId,
                                    @RequestParam(required = false) FlockStatus status) {
        return flocks.find(houseId, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Партия: возраст, поголовье, итоги учёта")
    public FlockResponse get(@PathVariable UUID id) {
        return flocks.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Посадить партию (технолог, администратор)")
    public FlockResponse create(@Valid @RequestBody FlockRequest request) {
        return flocks.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить партию (технолог, администратор)")
    public FlockResponse update(@PathVariable UUID id, @Valid @RequestBody FlockRequest request) {
        return flocks.update(id, request);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Закрыть партию: дата убоя, сдано голов и живого веса (технолог, администратор)")
    public FlockResponse close(@PathVariable UUID id, @Valid @RequestBody CloseFlockRequest request) {
        return flocks.close(id, request);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Журнал действий по партии: посадка, правки, учёт, взвешивания")
    public List<AuditLogEntry> history(@PathVariable UUID id) {
        flocks.getVisible(id);
        return audit.find(AuditService.FLOCK, id.toString(), 500);
    }

    @GetMapping("/{id}/daily-records")
    @Operation(summary = "Ежедневный учёт по партии")
    public List<DailyRecordResponse> dailyRecords(@PathVariable UUID id) {
        return journal.findDailyRecords(id);
    }

    @PostMapping("/{id}/daily-records")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Внести учёт за сутки")
    public DailyRecordResponse createDailyRecord(@PathVariable UUID id, @Valid @RequestBody DailyRecordRequest request) {
        return journal.createDailyRecord(id, request);
    }

    @PutMapping("/{id}/daily-records/{recordId}")
    @Operation(summary = "Исправить учёт за сутки (правка попадает в журнал)")
    public DailyRecordResponse updateDailyRecord(@PathVariable UUID id, @PathVariable UUID recordId,
                                                 @Valid @RequestBody DailyRecordRequest request) {
        return journal.updateDailyRecord(id, recordId, request);
    }

    @DeleteMapping("/{id}/daily-records/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить запись учёта (технолог, администратор)")
    public void deleteDailyRecord(@PathVariable UUID id, @PathVariable UUID recordId) {
        journal.deleteDailyRecord(id, recordId);
    }

    @GetMapping("/{id}/weighings")
    @Operation(summary = "Контрольные взвешивания")
    public List<WeighingResponse> weighings(@PathVariable UUID id) {
        return journal.findWeighings(id);
    }

    @PostMapping("/{id}/weighings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Внести взвешивание")
    public WeighingResponse createWeighing(@PathVariable UUID id, @Valid @RequestBody WeighingRequest request) {
        return journal.createWeighing(id, request);
    }

    @DeleteMapping("/{id}/weighings/{weighingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить взвешивание")
    public void deleteWeighing(@PathVariable UUID id, @PathVariable UUID weighingId) {
        journal.deleteWeighing(id, weighingId);
    }
}
