package com.broiler_monitoring.service;

import com.broiler_monitoring.dto.production.HouseRequest;
import com.broiler_monitoring.dto.production.HouseResponse;
import com.broiler_monitoring.dto.production.SiteRequest;
import com.broiler_monitoring.dto.production.ZoneRequest;
import com.broiler_monitoring.dto.production.ZoneResponse;
import com.broiler_monitoring.entity.Breed;
import com.broiler_monitoring.entity.Flock;
import com.broiler_monitoring.entity.House;
import com.broiler_monitoring.entity.Site;
import com.broiler_monitoring.entity.Zone;
import com.broiler_monitoring.enumerated.FlockStatus;
import com.broiler_monitoring.repository.BreedRepository;
import com.broiler_monitoring.repository.FlockRepository;
import com.broiler_monitoring.repository.HouseRepository;
import com.broiler_monitoring.repository.SiteRepository;
import com.broiler_monitoring.repository.ZoneRepository;
import com.broiler_monitoring.security.AccessService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Справочник структуры производства: площадки, птичники, зоны, кроссы (S2-01). */
@Service
public class StructureService {

    private final SiteRepository sites;
    private final HouseRepository houses;
    private final ZoneRepository zones;
    private final BreedRepository breeds;
    private final FlockRepository flocks;
    private final AccessService access;
    private final AuditService audit;

    public StructureService(SiteRepository sites, HouseRepository houses, ZoneRepository zones,
                            BreedRepository breeds, FlockRepository flocks, AccessService access, AuditService audit) {
        this.sites = sites;
        this.houses = houses;
        this.zones = zones;
        this.breeds = breeds;
        this.flocks = flocks;
        this.access = access;
        this.audit = audit;
    }

    public List<Site> findSites() {
        return sites.findAll(Sort.by("code"));
    }

    public Site getSite(UUID id) {
        return sites.findById(id).orElseThrow(() -> notFound("Площадка", id));
    }

    @Transactional
    public Site createSite(SiteRequest request) {
        if (sites.existsByCodeIgnoreCase(request.code().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Площадка с кодом '%s' уже есть".formatted(request.code()));
        }
        Site site = new Site();
        site.setCode(request.code().trim());
        site.setName(request.name().trim());
        site.setAddress(request.address());
        site.setTimezone(validTimezone(request.timezone()));
        return sites.save(site);
    }

    @Transactional(readOnly = true)
    public List<HouseResponse> findHouses() {
        Set<UUID> visible = access.visibleHouseIds();
        Map<UUID, Site> siteById = sites.findAll().stream().collect(Collectors.toMap(Site::getId, Function.identity()));
        Map<UUID, UUID> activeFlockByHouse = flocks.findByStatus(FlockStatus.ACTIVE).stream()
                .collect(Collectors.toMap(Flock::getHouseId, Flock::getId, (a, b) -> a));
        return houses.findAllByOrderByCodeAsc().stream()
                .filter(house -> visible == null || visible.contains(house.getId()))
                .map(house -> HouseResponse.from(house, siteById.get(house.getSiteId()),
                        zones.findByHouseIdOrderByCodeAsc(house.getId()), activeFlockByHouse.get(house.getId())))
                .toList();
    }

    public House getHouse(UUID id) {
        return houses.findById(id).orElseThrow(() -> notFound("Птичник", id));
    }

    @Transactional(readOnly = true)
    public HouseResponse getHouseResponse(UUID id) {
        access.requireHouse(id);
        House house = getHouse(id);
        UUID activeFlockId = flocks.findFirstByHouseIdAndStatus(id, FlockStatus.ACTIVE).map(Flock::getId).orElse(null);
        return HouseResponse.from(house, sites.findById(house.getSiteId()).orElse(null),
                zones.findByHouseIdOrderByCodeAsc(id), activeFlockId);
    }

    @Transactional
    public HouseResponse createHouse(HouseRequest request) {
        getSite(request.siteId());
        if (houses.existsBySiteIdAndCodeIgnoreCase(request.siteId(), request.code().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Птичник с кодом '%s' уже есть на площадке".formatted(request.code()));
        }
        House house = new House();
        apply(house, request);
        house = houses.save(house);
        audit.record(AuditService.HOUSE, house.getId(), "CREATED", "Создан птичник %s".formatted(house.getName()));
        return getHouseResponse(house.getId());
    }

    @Transactional
    public HouseResponse updateHouse(UUID id, HouseRequest request) {
        House house = getHouse(id);
        getSite(request.siteId());
        AuditService.Changes changes = AuditService.changes()
                .field("Код", house.getCode(), request.code().trim())
                .field("Название", house.getName(), request.name().trim())
                .field("Площадь, м²", house.getAreaM2(), request.areaM2())
                .field("Вместимость, гол", house.getCapacityHeads(), request.capacityHeads());
        apply(house, request);
        houses.save(house);
        audit.record(AuditService.HOUSE, id, "UPDATED", "Изменён птичник %s".formatted(house.getName()), changes);
        return getHouseResponse(id);
    }

    public List<ZoneResponse> findZones(UUID houseId) {
        access.requireHouse(houseId);
        return zones.findByHouseIdOrderByCodeAsc(houseId).stream().map(ZoneResponse::from).toList();
    }

    public Zone getZone(UUID id) {
        return zones.findById(id).orElseThrow(() -> notFound("Зона", id));
    }

    @Transactional
    public ZoneResponse createZone(UUID houseId, ZoneRequest request) {
        getHouse(houseId);
        if (zones.existsByHouseIdAndCodeIgnoreCase(houseId, request.code().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Зона с кодом '%s' уже есть в птичнике".formatted(request.code()));
        }
        Zone zone = new Zone();
        zone.setHouseId(houseId);
        zone.setCode(request.code().trim());
        zone.setName(request.name().trim());
        zone.setLayout(request.layout());
        return ZoneResponse.from(zones.save(zone));
    }

    @Transactional
    public ZoneResponse updateZone(UUID id, ZoneRequest request) {
        Zone zone = getZone(id);
        zone.setCode(request.code().trim());
        zone.setName(request.name().trim());
        zone.setLayout(request.layout());
        return ZoneResponse.from(zones.save(zone));
    }

    @Transactional
    public void deleteZone(UUID id) {
        zones.delete(getZone(id));
    }

    public List<Breed> findBreeds() {
        return breeds.findAll(Sort.by("code"));
    }

    public void requireBreed(String code) {
        if (code == null || !breeds.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный кросс '%s'".formatted(code));
        }
    }

    public ZoneId timezoneOfHouse(UUID houseId) {
        return houses.findById(houseId)
                .flatMap(house -> sites.findById(house.getSiteId()))
                .map(site -> ZoneId.of(site.getTimezone()))
                .orElse(ZoneId.of("Europe/Samara"));
    }

    private void apply(House house, HouseRequest request) {
        house.setSiteId(request.siteId());
        house.setCode(request.code().trim());
        house.setName(request.name().trim());
        house.setAreaM2(request.areaM2());
        house.setCapacityHeads(request.capacityHeads());
        house.setActive(request.active() == null || request.active());
    }

    private static String validTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "Europe/Samara";
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный часовой пояс '%s'".formatted(timezone));
        }
    }

    static ResponseStatusException notFound(String what, Object id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "%s '%s' не найден(а)".formatted(what, id));
    }
}
