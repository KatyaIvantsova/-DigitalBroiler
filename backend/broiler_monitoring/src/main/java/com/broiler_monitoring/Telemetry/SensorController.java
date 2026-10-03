package com.broiler_monitoring.Telemetry;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.broiler_monitoring.Telemetry.dto.SensorLocationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sensors")
@Tag(name = "Датчики", description = "Справочник датчиков: создание, обновление и поиск датчиков")
public class SensorController {
    private final SensorService sensorService;
    public SensorController(SensorService sensorService){
        this.sensorService = sensorService;
    }

    @GetMapping()
    @Operation(summary = "Получить все датчики", description = "Возвращает список всех датчиков, зарегистрированных в системе.")
    @ApiResponse(responseCode = "200", description = "Список датчиков получен")
    public List<Sensor> getAll(){
        return sensorService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить датчик по UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Датчик найден"),
            @ApiResponse(responseCode = "404", description = "Датчик не найден", content = @Content)
    })
    public Sensor getById(
            @Parameter(description = "UUID датчика", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID id
    ){
        return sensorService.getById(id);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Получить датчик по коду", description = "Код датчика используется gateway при отправке телеметрии.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Датчик найден"),
            @ApiResponse(responseCode = "404", description = "Датчик не найден", content = @Content)
    })
    public Sensor getByCode(
            @Parameter(description = "Уникальный код датчика", example = "TEMP-HOUSE-4-01")
            @PathVariable String code
    ){
        return sensorService.getByCode(code);
    }

    @PostMapping
    @Operation(summary = "Создать датчик", description = "Добавляет новый датчик в справочник. Поле code должно быть уникальным.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Датчик создан"),
            @ApiResponse(responseCode = "409", description = "Датчик с таким code уже существует", content = @Content)
    })
    public Sensor create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Данные нового датчика",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = Sensor.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "TEMP-HOUSE-4-01",
                                      "name": "Температура, птичник 4, зона 1",
                                      "type": "TEMPERATURE",
                                      "farm": "Ферма 1",
                                      "building": "Птичник 4",
                                      "unit": "C",
                                      "active": true
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody Sensor sensor
    ){
        return sensorService.create(sensor);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить датчик", description = "Обновляет основные поля датчика: code, name, type, farm, building, unit, active.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Датчик обновлен"),
            @ApiResponse(responseCode = "404", description = "Датчик не найден", content = @Content)
    })
    public Sensor update(
            @Parameter(description = "UUID датчика", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID id,
            @Valid @RequestBody Sensor sensor
    ){
        return sensorService.update(id, sensor);
    }

    @PatchMapping("/{id}/location")
    @Operation(summary = "Перенести датчик", description = "Привязывает датчик к птичнику и зоне. Доступно администратору.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Привязка изменена"),
            @ApiResponse(responseCode = "400", description = "Зона не из этого птичника", content = @Content),
            @ApiResponse(responseCode = "404", description = "Датчик, птичник или зона не найдены", content = @Content)
    })
    public Sensor updateLocation(@PathVariable UUID id, @RequestBody SensorLocationRequest request) {
        return sensorService.updateLocation(id, request);
    }
}
