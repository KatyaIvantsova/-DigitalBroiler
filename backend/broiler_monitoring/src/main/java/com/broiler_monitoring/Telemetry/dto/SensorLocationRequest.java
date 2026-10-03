package com.broiler_monitoring.Telemetry.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Куда установлен датчик. zoneId должен принадлежать houseId; null снимает привязку.")
public record SensorLocationRequest(UUID houseId, UUID zoneId) {
}
