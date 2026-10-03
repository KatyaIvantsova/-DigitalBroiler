package com.broiler_monitoring.Controller;

import com.broiler_monitoring.dto.production.HouseRequest;
import com.broiler_monitoring.dto.production.HouseResponse;
import com.broiler_monitoring.dto.production.SiteRequest;
import com.broiler_monitoring.dto.production.ZoneRequest;
import com.broiler_monitoring.dto.production.ZoneResponse;
import com.broiler_monitoring.entity.Breed;
import com.broiler_monitoring.entity.Site;
import com.broiler_monitoring.service.StructureService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Структура производства", description = "Площадки, птичники, зоны и кроссы")
public class StructureController {

    private final StructureService service;

    public StructureController(StructureService service) {
        this.service = service;
    }

    @GetMapping("/sites")
    @Operation(summary = "Площадки")
    public List<Site> sites() {
        return service.findSites();
    }

    @PostMapping("/sites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать площадку (администратор)")
    public Site createSite(@Valid @RequestBody SiteRequest request) {
        return service.createSite(request);
    }

    @GetMapping("/houses")
    @Operation(summary = "Птичники с зонами", description = "Оператор видит только птичники, на которые назначен.")
    public List<HouseResponse> houses() {
        return service.findHouses();
    }

    @GetMapping("/houses/{id}")
    @Operation(summary = "Птичник с зонами")
    public HouseResponse house(@PathVariable UUID id) {
        return service.getHouseResponse(id);
    }

    @PostMapping("/houses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать птичник (администратор)")
    public HouseResponse createHouse(@Valid @RequestBody HouseRequest request) {
        return service.createHouse(request);
    }

    @PutMapping("/houses/{id}")
    @Operation(summary = "Изменить птичник (администратор)")
    public HouseResponse updateHouse(@PathVariable UUID id, @Valid @RequestBody HouseRequest request) {
        return service.updateHouse(id, request);
    }

    @GetMapping("/houses/{houseId}/zones")
    @Operation(summary = "Зоны птичника")
    public List<ZoneResponse> zones(@PathVariable UUID houseId) {
        return service.findZones(houseId);
    }

    @PostMapping("/houses/{houseId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить зону (администратор)")
    public ZoneResponse createZone(@PathVariable UUID houseId, @Valid @RequestBody ZoneRequest request) {
        return service.createZone(houseId, request);
    }

    @PutMapping("/zones/{id}")
    @Operation(summary = "Изменить зону (администратор)")
    public ZoneResponse updateZone(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        return service.updateZone(id, request);
    }

    @DeleteMapping("/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить зону (администратор)")
    public void deleteZone(@PathVariable UUID id) {
        service.deleteZone(id);
    }

    @GetMapping("/breeds")
    @Operation(summary = "Кроссы")
    public List<Breed> breeds() {
        return service.findBreeds();
    }
}
