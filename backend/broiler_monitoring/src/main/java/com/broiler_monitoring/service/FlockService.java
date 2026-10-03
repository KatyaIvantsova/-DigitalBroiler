package com.broiler_monitoring.service;

import com.broiler_monitoring.dto.production.CloseFlockRequest;
import com.broiler_monitoring.dto.production.FlockRequest;
import com.broiler_monitoring.dto.production.FlockResponse;
import com.broiler_monitoring.entity.Breed;
import com.broiler_monitoring.entity.DailyRecord;
import com.broiler_monitoring.entity.Flock;
import com.broiler_monitoring.entity.House;
import com.broiler_monitoring.enumerated.FlockSex;
import com.broiler_monitoring.enumerated.FlockStatus;
import com.broiler_monitoring.enumerated.UserRole;
import com.broiler_monitoring.repository.BreedRepository;
import com.broiler_monitoring.repository.DailyRecordRepository;
import com.broiler_monitoring.repository.FlockRepository;
import com.broiler_monitoring.repository.HouseRepository;
import com.broiler_monitoring.security.AccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Партии: посадка, правка, закрытие, возраст и поголовье (S2-01, S2-02). */
@Service
public class FlockService {

    private static final Logger log = LoggerFactory.getLogger(FlockService.class);
    private static final DateTimeFormatter CODE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FlockRepository flocks;
    private final HouseRepository houses;
    private final BreedRepository breeds;
    private final DailyRecordRepository dailyRecords;
    private final StructureService structure;
    private final AccessService access;
    private final AuditService audit;
    private final Clock clock;

    public FlockService(FlockRepository flocks, HouseRepository houses, BreedRepository breeds,
                        DailyRecordRepository dailyRecords, StructureService structure,
                        AccessService access, AuditService audit, Clock clock) {
        this.flocks = flocks;
        this.houses = houses;
        this.breeds = breeds;
        this.dailyRecords = dailyRecords;
        this.structure = structure;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FlockResponse> find(UUID houseId, FlockStatus status) {
        Set<UUID> visible = access.visibleHouseIds();
        Map<UUID, House> houseById = houses.findAll().stream().collect(Collectors.toMap(House::getId, Function.identity()));
        Map<String, Breed> breedByCode = breeds.findAll().stream().collect(Collectors.toMap(Breed::getCode, Function.identity()));
        return flocks.findAllByOrderByPlacedAtDesc().stream()
                .filter(flock -> visible == null || visible.contains(flock.getHouseId()))
                .filter(flock -> houseId == null || houseId.equals(flock.getHouseId()))
                .filter(flock -> status == null || status == flock.getStatus())
                .map(flock -> toResponse(flock, houseById.get(flock.getHouseId()), breedByCode.get(flock.getBreedCode())))
                .toList();
    }

    public Flock getVisible(UUID id) {
        Flock flock = flocks.findById(id).orElseThrow(() -> StructureService.notFound("Партия", id));
        access.requireHouse(flock.getHouseId());
        return flock;
    }

    @Transactional(readOnly = true)
    public FlockResponse get(UUID id) {
        return toResponse(getVisible(id));
    }

    @Transactional
    public FlockResponse create(FlockRequest request) {
        House house = structure.getHouse(request.houseId());
        access.requireHouse(house.getId());
        structure.requireBreed(request.breedCode());

        LocalDate today = today(house.getId());
        Flock flock = new Flock();
        flock.setHouseId(house.getId());
        flock.setCode(resolveCode(request.code(), request.placedAt(), house));
        apply(flock, request);
        flock.setStatus(request.placedAt().isAfter(today) ? FlockStatus.PLANNED : FlockStatus.ACTIVE);
        if (flock.getStatus() == FlockStatus.ACTIVE) {
            requireNoOtherActive(flock);
        }
        flock = flocks.save(flock);

        audit.record(AuditService.FLOCK, flock.getId(), "CREATED",
                "Посадка партии %s в %s".formatted(flock.getCode(), house.getName()),
                AuditService.changes()
                        .value("Кросс", flock.getBreedCode())
                        .value("Дата посадки", flock.getPlacedAt())
                        .value("Посажено, гол", flock.getPlacedHeads()));
        return toResponse(flock);
    }

    @Transactional
    public FlockResponse update(UUID id, FlockRequest request) {
        Flock flock = getVisible(id);
        requireEditable(flock);
        structure.requireBreed(request.breedCode());
        if (!flock.getHouseId().equals(request.houseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Птичник партии не меняется — закройте партию и посадите новую");
        }
        AuditService.Changes changes = AuditService.changes()
                .field("Кросс", flock.getBreedCode(), request.breedCode())
                .field("Дата посадки", flock.getPlacedAt(), request.placedAt())
                .field("Посажено, гол", flock.getPlacedHeads(), request.placedHeads())
                .field("Вес при посадке, г", flock.getPlacedAvgWeightG(), request.placedAvgWeightG())
                .field("Пол", flock.getSex(), request.sex() != null ? request.sex() : flock.getSex())
                .field("Инкубаторий", flock.getHatchery(), request.hatchery())
                .field("Плановый срок, дн", flock.getTargetAgeDays(), request.targetAgeDays());
        if (request.code() != null && !request.code().isBlank() && !request.code().trim().equals(flock.getCode())) {
            if (flocks.existsByCodeIgnoreCase(request.code().trim())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Партия с кодом '%s' уже есть".formatted(request.code()));
            }
            changes.field("Код", flock.getCode(), request.code().trim());
            flock.setCode(request.code().trim());
        }
        apply(flock, request);
        int losses = lossesOf(flock.getId());
        if (losses > flock.getPlacedHeads()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Посажено меньше, чем уже списано по учёту (%d гол)".formatted(losses));
        }
        if (flock.getStatus() != FlockStatus.CLOSED) {
            flock.setStatus(flock.getPlacedAt().isAfter(today(flock.getHouseId())) ? FlockStatus.PLANNED : FlockStatus.ACTIVE);
            if (flock.getStatus() == FlockStatus.ACTIVE) {
                requireNoOtherActive(flock);
            }
        }
        flock = flocks.save(flock);
        audit.record(AuditService.FLOCK, flock.getId(), "UPDATED", "Изменена партия %s".formatted(flock.getCode()), changes);
        return toResponse(flock);
    }

    @Transactional
    public FlockResponse close(UUID id, CloseFlockRequest request) {
        Flock flock = getVisible(id);
        if (flock.getStatus() == FlockStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Партия уже закрыта");
        }
        if (flock.getStatus() == FlockStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Партия ещё не посажена");
        }
        if (request.closedAt().isBefore(flock.getPlacedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата закрытия раньше даты посадки");
        }
        if (request.closedAt().isAfter(today(flock.getHouseId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата закрытия в будущем");
        }
        int alive = flock.getPlacedHeads() - lossesOf(flock.getId());
        if (request.shippedHeads() > alive) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Сдано больше, чем живое поголовье по учёту (%d гол)".formatted(alive));
        }
        flock.setStatus(FlockStatus.CLOSED);
        flock.setClosedAt(request.closedAt());
        flock.setShippedHeads(request.shippedHeads());
        flock.setShippedLiveWeightKg(request.shippedLiveWeightKg());
        flock = flocks.save(flock);
        audit.record(AuditService.FLOCK, flock.getId(), "CLOSED", "Партия %s закрыта".formatted(flock.getCode()),
                AuditService.changes()
                        .value("Дата закрытия", flock.getClosedAt())
                        .value("Сдано, гол", flock.getShippedHeads())
                        .value("Сдано живого веса, кг", flock.getShippedLiveWeightKg()));
        return toResponse(flock);
    }

    /** Партии с наступившей датой посадки становятся активными (если птичник свободен). */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void activateDueFlocks() {
        for (Flock flock : flocks.findByStatus(FlockStatus.PLANNED)) {
            if (!flock.getPlacedAt().isAfter(today(flock.getHouseId()))
                    && flocks.findFirstByHouseIdAndStatus(flock.getHouseId(), FlockStatus.ACTIVE).isEmpty()) {
                flock.setStatus(FlockStatus.ACTIVE);
                flocks.save(flock);
                log.info("Партия {} переведена в активные", flock.getCode());
            }
        }
    }

    /** Активная партия птичника — к ней привязываются показания датчиков и инциденты. */
    public java.util.Optional<Flock> activeFlockOfHouse(UUID houseId) {
        return houseId == null ? java.util.Optional.empty() : flocks.findFirstByHouseIdAndStatus(houseId, FlockStatus.ACTIVE);
    }

    public LocalDate today(UUID houseId) {
        return LocalDate.now(clock.withZone(structure.timezoneOfHouse(houseId)));
    }

    /** Возраст партии в днях на дату (день посадки — 0). */
    public static int ageOn(Flock flock, LocalDate date) {
        return (int) ChronoUnit.DAYS.between(flock.getPlacedAt(), date);
    }

    public Integer ageDays(Flock flock) {
        if (flock.getStatus() == FlockStatus.CLOSED && flock.getClosedAt() != null) {
            return ageOn(flock, flock.getClosedAt());
        }
        int age = ageOn(flock, today(flock.getHouseId()));
        return age < 0 ? null : age;
    }

    /** Закрытую партию правят только технолог и администратор (docs/sprint1/02-data-model.md, п. 3). */
    public void requireEditable(Flock flock) {
        if (flock.getStatus() == FlockStatus.CLOSED) {
            access.requireRole("Закрытую партию правят только технолог и администратор",
                    UserRole.TECHNOLOGIST, UserRole.ADMIN);
        }
    }

    FlockResponse toResponse(Flock flock) {
        return toResponse(flock, houses.findById(flock.getHouseId()).orElse(null),
                breeds.findById(flock.getBreedCode()).orElse(null));
    }

    private FlockResponse toResponse(Flock flock, House house, Breed breed) {
        List<DailyRecord> records = dailyRecords.findByFlockIdOrderByRecordDateAsc(flock.getId());
        int mortality = records.stream().mapToInt(DailyRecord::getMortalityHeads).sum();
        int culled = records.stream().mapToInt(DailyRecord::getCulledHeads).sum();
        return new FlockResponse(
                flock.getId(), flock.getHouseId(), house != null ? house.getName() : null,
                flock.getCode(), flock.getBreedCode(), breed != null ? breed.getName() : flock.getBreedCode(),
                flock.getPlacedAt(), flock.getPlacedHeads(), flock.getPlacedAvgWeightG(), flock.getSex(),
                flock.getHatchery(), flock.getTargetAgeDays(), flock.getStatus(), flock.getClosedAt(),
                flock.getShippedHeads(), flock.getShippedLiveWeightKg(), ageDays(flock),
                mortality, culled, flock.getPlacedHeads() - mortality - culled);
    }

    int lossesOf(UUID flockId) {
        return dailyRecords.findByFlockIdOrderByRecordDateAsc(flockId).stream()
                .mapToInt(record -> record.getMortalityHeads() + record.getCulledHeads())
                .sum();
    }

    private void apply(Flock flock, FlockRequest request) {
        flock.setBreedCode(request.breedCode());
        flock.setPlacedAt(request.placedAt());
        flock.setPlacedHeads(request.placedHeads());
        flock.setPlacedAvgWeightG(request.placedAvgWeightG());
        flock.setSex(request.sex() != null ? request.sex() : FlockSex.MIXED);
        flock.setHatchery(blankToNull(request.hatchery()));
        flock.setTargetAgeDays(request.targetAgeDays());
    }

    private void requireNoOtherActive(Flock flock) {
        flocks.findFirstByHouseIdAndStatus(flock.getHouseId(), FlockStatus.ACTIVE)
                .filter(other -> !other.getId().equals(flock.getId()))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "В птичнике уже есть активная партия %s — сначала закройте её".formatted(other.getCode()));
                });
    }

    private String resolveCode(String requested, LocalDate placedAt, House house) {
        if (requested != null && !requested.isBlank()) {
            String code = requested.trim();
            if (flocks.existsByCodeIgnoreCase(code)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Партия с кодом '%s' уже есть".formatted(code));
            }
            return code;
        }
        String base = placedAt.format(CODE_MONTH) + "-" + house.getCode();
        String code = base;
        for (int suffix = 2; flocks.existsByCodeIgnoreCase(code); suffix++) {
            code = base + "-" + suffix;
        }
        return code;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
