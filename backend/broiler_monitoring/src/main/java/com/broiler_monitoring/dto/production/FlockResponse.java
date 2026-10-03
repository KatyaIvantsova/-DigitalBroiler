package com.broiler_monitoring.dto.production;

import com.broiler_monitoring.enumerated.FlockSex;
import com.broiler_monitoring.enumerated.FlockStatus;

import java.time.LocalDate;
import java.util.UUID;

public record FlockResponse(
        UUID id,
        UUID houseId,
        String houseName,
        String code,
        String breedCode,
        String breedName,
        LocalDate placedAt,
        Integer placedHeads,
        Double placedAvgWeightG,
        FlockSex sex,
        String hatchery,
        Integer targetAgeDays,
        FlockStatus status,
        LocalDate closedAt,
        Integer shippedHeads,
        Double shippedLiveWeightKg,
        /** Возраст в днях: сегодня (по часовому поясу площадки) − дата посадки; для закрытой — длительность цикла. */
        Integer ageDays,
        int mortalityTotal,
        int culledTotal,
        /** Живое поголовье: посажено − падёж − выбраковка. */
        int currentHeads
) {
}
