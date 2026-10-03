package com.broiler_monitoring.dto.production;

import com.broiler_monitoring.entity.Zone;

import java.util.UUID;

public record ZoneResponse(UUID id, UUID houseId, String code, String name, String layout) {
    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getHouseId(), zone.getCode(), zone.getName(), zone.getLayout());
    }
}
