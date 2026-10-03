package com.broiler_monitoring.Telemetry;

import com.broiler_monitoring.Telemetry.dto.SensorLocationRequest;
import com.broiler_monitoring.entity.House;
import com.broiler_monitoring.entity.Site;
import com.broiler_monitoring.entity.Zone;
import com.broiler_monitoring.service.AuditService;
import com.broiler_monitoring.service.StructureService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SensorService {

    private final SensorRepository repository;
    private final StructureService structure;
    private final AuditService audit;

    public SensorService(SensorRepository repository, StructureService structure, AuditService audit){
        this.repository = repository;
        this.structure = structure;
        this.audit = audit;
    }
    public List<Sensor> findAll(){
        return repository.findAll();
    }
    public Sensor getById(UUID id){
        return repository.findById(id).orElseThrow(()-> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Sensor with id '%s' not found".formatted(id)));
    }
    public Sensor getByCode(String code){
        return repository.findByCode(code)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sensor with code '%s' not found".formatted(code)));
    }
    public Sensor create(Sensor sensor){
        if (repository.existsByCode(sensor.getCode())){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sensor with code '%s' already exists".formatted(sensor.getCode()));
        }
        return repository.save(sensor);
    }
    public Sensor update(UUID id,Sensor updatedSensor){
        Sensor sensor = getById(id);

        sensor.setCode(updatedSensor.getCode());
        sensor.setName(updatedSensor.getName());
        sensor.setType(updatedSensor.getType());
        sensor.setFarm(updatedSensor.getFarm());
        sensor.setBuilding(updatedSensor.getBuilding());
        sensor.setUnit(updatedSensor.getUnit());
        sensor.setActive(updatedSensor.getActive());

        return repository.save(sensor);
    }

    /**
     * Перенос датчика в другой птичник или зону (S2-07) без правки кода и миграций.
     * Старые показания в InfluxDB не переписываются: привязка «датчик → зона» хранится только в Postgres.
     */
    @Transactional
    public Sensor updateLocation(UUID id, SensorLocationRequest request){
        Sensor sensor = getById(id);
        UUID houseId = request.houseId();
        UUID zoneId = request.zoneId();
        String houseName = null;
        String zoneName = null;
        if (zoneId != null) {
            Zone zone = structure.getZone(zoneId);
            if (houseId == null) {
                houseId = zone.getHouseId();
            } else if (!zone.getHouseId().equals(houseId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Зона не принадлежит выбранному птичнику");
            }
            zoneName = zone.getName();
        }
        if (houseId != null) {
            House house = structure.getHouse(houseId);
            Site site = structure.getSite(house.getSiteId());
            houseName = house.getName();
            sensor.setBuilding(house.getName());
            sensor.setFarm(site.getName());
        }
        AuditService.Changes changes = AuditService.changes()
                .field("Птичник", sensor.getHouseId(), houseId)
                .field("Зона", sensor.getZoneId(), zoneId);
        sensor.setHouseId(houseId);
        sensor.setZoneId(zoneId);
        sensor = repository.save(sensor);
        audit.record(AuditService.SENSOR, sensor.getId(), "MOVED",
                "Датчик %s перенесён: %s / %s".formatted(sensor.getCode(),
                        houseName != null ? houseName : "без птичника", zoneName != null ? zoneName : "без зоны"),
                changes);
        return sensor;
    }



}
