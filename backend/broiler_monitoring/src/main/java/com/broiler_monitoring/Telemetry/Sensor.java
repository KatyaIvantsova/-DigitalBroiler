package com.broiler_monitoring.Telemetry;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "sensors")
@Schema(description = "Датчик, зарегистрированный в системе мониторинга")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "Внутренний UUID датчика", example = "11111111-1111-1111-1111-111111111111", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true)
    @Schema(description = "Уникальный код датчика, который отправляет gateway", example = "TEMP-HOUSE-4-01")
    private String code;

    @NotBlank
    @Column(nullable = false)
    @Schema(description = "Человекочитаемое название датчика", example = "Температура, птичник 4, зона 1")
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Schema(description = "Тип датчика", example = "TEMPERATURE")
    private SensorType type;

    @NotBlank
    @Column(nullable = false)
    @Schema(description = "Ферма, на которой установлен датчик", example = "Ферма 1")
    private String farm;

    @NotBlank
    @Column(nullable = false)
    @Schema(description = "Птичник или корпус, где установлен датчик", example = "Птичник 4")
    private String building;

    @NotBlank
    @Column(nullable = false)
    @Schema(description = "Единица измерения", example = "C")
    private String unit;

    @Schema(description = "UUID птичника, где установлен датчик", example = "b0000000-0000-0000-0000-000000000004")
    private UUID houseId;

    @Schema(description = "UUID зоны птичника", example = "c0000000-0000-0000-0000-000000000401")
    private UUID zoneId;

    @Column(nullable = false)
    @Schema(description = "Активен ли датчик и можно ли принимать от него показания", example = "true")
    private Boolean active = true;

    @Column(nullable = false)
    @Schema(description = "Дата создания записи о датчике", example = "2026-05-14T13:34:31Z", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant createdAt;

    @Schema(description = "Дата последнего обновления записи о датчике", example = "2026-05-14T13:44:31Z", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant updatedAt;

    @PrePersist
    public void prePersist(){
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (active ==null)
            active = true;
    }
    @PreUpdate
    public void preUpdate(){
        updatedAt = Instant.now();
    }

}
