package com.broiler_monitoring.dto.production;

import com.broiler_monitoring.entity.Weighing;
import com.broiler_monitoring.enumerated.WeighingMethod;

import java.time.Instant;
import java.util.UUID;

public record WeighingResponse(
        UUID id,
        UUID flockId,
        UUID zoneId,
        Instant weighedAt,
        int ageDay,
        Integer sampleHeads,
        Double avgWeightG,
        Double uniformityPct,
        WeighingMethod method
) {
    public static WeighingResponse from(Weighing weighing, int ageDay) {
        return new WeighingResponse(weighing.getId(), weighing.getFlockId(), weighing.getZoneId(),
                weighing.getWeighedAt(), ageDay, weighing.getSampleHeads(), weighing.getAvgWeightG(),
                weighing.getUniformityPct(), weighing.getMethod());
    }
}
