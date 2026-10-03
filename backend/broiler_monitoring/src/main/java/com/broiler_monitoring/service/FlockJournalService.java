package com.broiler_monitoring.service;

import com.broiler_monitoring.dto.production.DailyRecordRequest;
import com.broiler_monitoring.dto.production.DailyRecordResponse;
import com.broiler_monitoring.dto.production.WeighingRequest;
import com.broiler_monitoring.dto.production.WeighingResponse;
import com.broiler_monitoring.entity.DailyRecord;
import com.broiler_monitoring.entity.Flock;
import com.broiler_monitoring.entity.Weighing;
import com.broiler_monitoring.entity.Zone;
import com.broiler_monitoring.enumerated.FlockStatus;
import com.broiler_monitoring.enumerated.UserRole;
import com.broiler_monitoring.enumerated.WeighingMethod;
import com.broiler_monitoring.repository.DailyRecordRepository;
import com.broiler_monitoring.repository.WeighingRepository;
import com.broiler_monitoring.rules.FlockRecordRuleService;
import com.broiler_monitoring.security.AccessService;
import com.broiler_monitoring.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ежедневный учёт по партии (S2-03): падёж, выбраковка, корм, вода и контрольные взвешивания.
 * Каждая правка пишется в журнал действий партии с полями «было → стало».
 */
@Service
public class FlockJournalService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final FlockService flocks;
    private final DailyRecordRepository dailyRecords;
    private final WeighingRepository weighings;
    private final StructureService structure;
    private final AccessService access;
    private final AuditService audit;
    private final Clock clock;
    private final FlockRecordRuleService recordRules;

    public FlockJournalService(FlockService flocks, DailyRecordRepository dailyRecords, WeighingRepository weighings,
                               StructureService structure, AccessService access, AuditService audit, Clock clock,
                               FlockRecordRuleService recordRules) {
        this.flocks = flocks;
        this.dailyRecords = dailyRecords;
        this.weighings = weighings;
        this.structure = structure;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.recordRules = recordRules;
    }

    @Transactional(readOnly = true)
    public List<DailyRecordResponse> findDailyRecords(UUID flockId) {
        Flock flock = flocks.getVisible(flockId);
        List<DailyRecordResponse> result = new ArrayList<>();
        int heads = flock.getPlacedHeads();
        for (DailyRecord record : dailyRecords.findByFlockIdOrderByRecordDateAsc(flockId)) {
            heads -= record.getMortalityHeads() + record.getCulledHeads();
            result.add(toResponse(flock, record, heads));
        }
        return result;
    }

    @Transactional
    public DailyRecordResponse createDailyRecord(UUID flockId, DailyRecordRequest request) {
        Flock flock = writableFlock(flockId);
        validateDate(flock, request.recordDate());
        if (dailyRecords.findByFlockIdAndRecordDate(flockId, request.recordDate()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Учёт за %s уже внесён — исправьте существующую запись".formatted(request.recordDate().format(DAY)));
        }
        DailyRecord record = new DailyRecord();
        record.setFlockId(flockId);
        record.setRecordDate(request.recordDate());
        record.setCreatedBy(CurrentActor.get().id());
        apply(record, request);
        validateLosses(flock, record);
        record = dailyRecords.save(record);

        audit.record(AuditService.FLOCK, flockId, "DAILY_RECORD_CREATED",
                "Учёт за %s внесён".formatted(record.getRecordDate().format(DAY)),
                AuditService.changes()
                        .value("Падёж, гол", record.getMortalityHeads())
                        .value("Выбраковка, гол", record.getCulledHeads())
                        .value("Корм, кг", record.getFeedConsumedKg())
                        .value("Вода, л", record.getWaterConsumedL()));
        recordRules.recordChanged(flock, record.getRecordDate());
        return findResponse(flock, record.getId());
    }

    @Transactional
    public DailyRecordResponse updateDailyRecord(UUID flockId, UUID recordId, DailyRecordRequest request) {
        Flock flock = writableFlock(flockId);
        DailyRecord record = dailyRecords.findById(recordId)
                .filter(candidate -> candidate.getFlockId().equals(flockId))
                .orElseThrow(() -> StructureService.notFound("Запись учёта", recordId));
        if (!record.getRecordDate().equals(request.recordDate())) {
            validateDate(flock, request.recordDate());
            if (dailyRecords.findByFlockIdAndRecordDate(flockId, request.recordDate()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Учёт за %s уже внесён".formatted(request.recordDate().format(DAY)));
            }
        }
        AuditService.Changes changes = AuditService.changes()
                .field("Дата", record.getRecordDate(), request.recordDate())
                .field("Падёж, гол", record.getMortalityHeads(), request.mortalityHeads())
                .field("Выбраковка, гол", record.getCulledHeads(), request.culledHeads())
                .field("Корм, кг", record.getFeedConsumedKg(), request.feedConsumedKg())
                .field("Вода, л", record.getWaterConsumedL(), request.waterConsumedL())
                .field("Комментарий", record.getComment(), blankToNull(request.comment()));
        LocalDate previousDate = record.getRecordDate();
        record.setRecordDate(request.recordDate());
        record.setUpdatedBy(CurrentActor.get().id());
        apply(record, request);
        validateLosses(flock, record);
        dailyRecords.save(record);

        if (!changes.isEmpty()) {
            audit.record(AuditService.FLOCK, flockId, "DAILY_RECORD_UPDATED",
                    "Исправлен учёт за %s".formatted(record.getRecordDate().format(DAY)), changes);
        }
        if (!previousDate.equals(record.getRecordDate())) {
            recordRules.recordChanged(flock, previousDate);
        }
        recordRules.recordChanged(flock, record.getRecordDate());
        return findResponse(flock, recordId);
    }

    @Transactional
    public void deleteDailyRecord(UUID flockId, UUID recordId) {
        Flock flock = writableFlock(flockId);
        access.requireRole("Удалять записи учёта могут технолог и администратор", UserRole.TECHNOLOGIST, UserRole.ADMIN);
        DailyRecord record = dailyRecords.findById(recordId)
                .filter(candidate -> candidate.getFlockId().equals(flock.getId()))
                .orElseThrow(() -> StructureService.notFound("Запись учёта", recordId));
        dailyRecords.delete(record);
        audit.record(AuditService.FLOCK, flockId, "DAILY_RECORD_DELETED",
                "Удалён учёт за %s".formatted(record.getRecordDate().format(DAY)),
                AuditService.changes()
                        .value("Падёж, гол", record.getMortalityHeads())
                        .value("Выбраковка, гол", record.getCulledHeads()));
        recordRules.recordChanged(flock, record.getRecordDate());
    }

    @Transactional(readOnly = true)
    public List<WeighingResponse> findWeighings(UUID flockId) {
        Flock flock = flocks.getVisible(flockId);
        return weighings.findByFlockIdOrderByWeighedAtAsc(flockId).stream()
                .map(weighing -> WeighingResponse.from(weighing, ageAt(flock, weighing.getWeighedAt())))
                .toList();
    }

    @Transactional
    public WeighingResponse createWeighing(UUID flockId, WeighingRequest request) {
        Flock flock = writableFlock(flockId);
        Instant weighedAt = request.weighedAt() != null ? request.weighedAt() : clock.instant();
        LocalDate date = weighedAt.atZone(structure.timezoneOfHouse(flock.getHouseId())).toLocalDate();
        validateDate(flock, date);
        if (request.zoneId() != null) {
            Zone zone = structure.getZone(request.zoneId());
            if (!zone.getHouseId().equals(flock.getHouseId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Зона не из птичника партии");
            }
        }
        Weighing weighing = new Weighing();
        weighing.setFlockId(flockId);
        weighing.setZoneId(request.zoneId());
        weighing.setWeighedAt(weighedAt);
        weighing.setSampleHeads(request.sampleHeads());
        weighing.setAvgWeightG(request.avgWeightG());
        weighing.setUniformityPct(request.uniformityPct());
        weighing.setMethod(request.method() != null ? request.method() : WeighingMethod.MANUAL);
        weighing.setCreatedBy(CurrentActor.get().id());
        weighing = weighings.save(weighing);

        audit.record(AuditService.FLOCK, flockId, "WEIGHING_CREATED",
                "Взвешивание %s: %.0f г".formatted(date.format(DAY), weighing.getAvgWeightG()),
                AuditService.changes()
                        .value("Выборка, гол", weighing.getSampleHeads())
                        .value("Однородность, %", weighing.getUniformityPct()));
        return WeighingResponse.from(weighing, FlockService.ageOn(flock, date));
    }

    @Transactional
    public void deleteWeighing(UUID flockId, UUID weighingId) {
        Flock flock = writableFlock(flockId);
        Weighing weighing = weighings.findById(weighingId)
                .filter(candidate -> candidate.getFlockId().equals(flock.getId()))
                .orElseThrow(() -> StructureService.notFound("Взвешивание", weighingId));
        weighings.delete(weighing);
        audit.record(AuditService.FLOCK, flockId, "WEIGHING_DELETED",
                "Удалено взвешивание %.0f г".formatted(weighing.getAvgWeightG()));
    }

    private Flock writableFlock(UUID flockId) {
        Flock flock = flocks.getVisible(flockId);
        if (flock.getStatus() == FlockStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Партия ещё не посажена");
        }
        flocks.requireEditable(flock);
        return flock;
    }

    private void validateDate(Flock flock, LocalDate date) {
        LocalDate last = flock.getClosedAt() != null ? flock.getClosedAt() : flocks.today(flock.getHouseId());
        if (date.isBefore(flock.getPlacedAt()) || date.isAfter(last)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Дата %s вне периода партии (%s — %s)".formatted(
                            date.format(DAY), flock.getPlacedAt().format(DAY), last.format(DAY)));
        }
    }

    private void validateLosses(Flock flock, DailyRecord changed) {
        int losses = dailyRecords.findByFlockIdOrderByRecordDateAsc(flock.getId()).stream()
                .filter(record -> !record.getId().equals(changed.getId()))
                .mapToInt(record -> record.getMortalityHeads() + record.getCulledHeads())
                .sum() + changed.getMortalityHeads() + changed.getCulledHeads();
        if (losses > flock.getPlacedHeads()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Списано больше, чем посажено: %d из %d гол".formatted(losses, flock.getPlacedHeads()));
        }
    }

    private DailyRecordResponse findResponse(Flock flock, UUID recordId) {
        return findDailyRecords(flock.getId()).stream()
                .filter(response -> response.id().equals(recordId))
                .findFirst()
                .orElseThrow();
    }

    private DailyRecordResponse toResponse(Flock flock, DailyRecord record, int headsAtEnd) {
        return new DailyRecordResponse(record.getId(), record.getFlockId(), record.getRecordDate(),
                FlockService.ageOn(flock, record.getRecordDate()), record.getMortalityHeads(), record.getCulledHeads(),
                record.getFeedConsumedKg(), record.getWaterConsumedL(), record.getComment(), headsAtEnd,
                record.getCreatedAt(), record.getUpdatedAt());
    }

    private int ageAt(Flock flock, Instant instant) {
        return FlockService.ageOn(flock, instant.atZone(structure.timezoneOfHouse(flock.getHouseId())).toLocalDate());
    }

    private static void apply(DailyRecord record, DailyRecordRequest request) {
        record.setMortalityHeads(request.mortalityHeads());
        record.setCulledHeads(request.culledHeads());
        record.setFeedConsumedKg(request.feedConsumedKg());
        record.setWaterConsumedL(request.waterConsumedL());
        record.setComment(blankToNull(request.comment()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
