package com.broiler_monitoring.dto.production;

import com.broiler_monitoring.entity.House;
import com.broiler_monitoring.entity.Site;
import com.broiler_monitoring.entity.Zone;

import java.util.List;
import java.util.UUID;

public record HouseResponse(
        UUID id,
        UUID siteId,
        String siteName,
        String code,
        String name,
        Double areaM2,
        Integer capacityHeads,
        boolean active,
        List<ZoneResponse> zones,
        UUID activeFlockId
) {
    public static HouseResponse from(House house, Site site, List<Zone> zones, UUID activeFlockId) {
        return new HouseResponse(
                house.getId(), house.getSiteId(), site != null ? site.getName() : null,
                house.getCode(), house.getName(), house.getAreaM2(), house.getCapacityHeads(), house.isActive(),
                zones.stream().map(ZoneResponse::from).toList(), activeFlockId);
    }
}
