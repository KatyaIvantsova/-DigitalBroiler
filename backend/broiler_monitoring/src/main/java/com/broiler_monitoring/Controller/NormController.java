package com.broiler_monitoring.Controller;

import com.broiler_monitoring.dto.norms.NormImportResult;
import com.broiler_monitoring.dto.norms.NormUpdateRequest;
import com.broiler_monitoring.dto.norms.RuleUpdateRequest;
import com.broiler_monitoring.rules.RuleAdminService;
import com.broiler_monitoring.rules.RuleEvaluationService;
import com.broiler_monitoring.entity.Norm;
import com.broiler_monitoring.entity.Rule;
import com.broiler_monitoring.enumerated.NormMetric;
import com.broiler_monitoring.repository.RuleRepository;
import com.broiler_monitoring.service.NormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Нормы и правила", description = "Справочник норм по возрасту и кроссу, правила движка")
public class NormController {

    private final NormService norms;
    private final RuleRepository rules;
    private final RuleAdminService ruleAdmin;
    private final RuleEvaluationService evaluation;

    public NormController(NormService norms, RuleRepository rules, RuleAdminService ruleAdmin, RuleEvaluationService evaluation) {
        this.norms = norms;
        this.rules = rules;
        this.ruleAdmin = ruleAdmin;
        this.evaluation = evaluation;
    }

    @GetMapping("/norms")
    @Operation(summary = "Действующие нормы", description = "Фильтры: показатель, кросс (вместе с общими для всех кроссов).")
    public List<Norm> current(@RequestParam(required = false) NormMetric metric,
                              @RequestParam(required = false) String breedCode) {
        return norms.findCurrent(metric, breedCode);
    }

    @GetMapping("/norms/lookup")
    @Operation(summary = "Норма для показателя, кросса и возраста")
    public ResponseEntity<Norm> lookup(@RequestParam NormMetric metric, @RequestParam String breedCode, @RequestParam int ageDay) {
        Optional<Norm> norm = norms.find(metric, breedCode, ageDay);
        return norm.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/norms/{id}/history")
    @Operation(summary = "Все версии нормы, новые сверху")
    public List<Norm> history(@PathVariable UUID id) {
        return norms.history(id);
    }

    @PutMapping("/norms/{id}")
    @Operation(summary = "Изменить норму (технолог, администратор): создаётся новая версия")
    public Norm update(@PathVariable UUID id, @Valid @RequestBody NormUpdateRequest request) {
        return norms.update(id, request);
    }

    @PostMapping(value = "/norms/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Импорт таблицы норм из CSV (технолог, администратор)")
    public NormImportResult importCsv(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "Импорт таблицы норм") String comment) throws IOException {
        return norms.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8), comment);
    }

    @GetMapping(value = "/norms/export", produces = "text/csv")
    @Operation(summary = "Выгрузка действующих норм в CSV (формат импорта)")
    public ResponseEntity<String> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"norms.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(norms.exportCsv());
    }

    @GetMapping("/rules")
    @Operation(summary = "Правила движка и их пороги")
    public List<Rule> rules() {
        return rules.findAll(Sort.by("code"));
    }

    @PutMapping("/rules/{code}")
    @Operation(summary = "Изменить пороги правила (технолог, администратор)", description = "Применяется со следующего цикла движка.")
    public Rule updateRule(@PathVariable String code, @Valid @RequestBody RuleUpdateRequest request) {
        return ruleAdmin.update(code, request);
    }

    @PostMapping("/rules/evaluate")
    @Operation(summary = "Проверить правила сейчас (технолог, администратор)",
            description = "Внеочередной цикл движка: сколько датчиков проверено, сколько инцидентов создано или повышено и закрыто.")
    public RuleEvaluationService.Summary evaluate() {
        evaluation.resetLightingProgramSchedule();
        return evaluation.evaluateAll(java.time.Instant.now());
    }
}
