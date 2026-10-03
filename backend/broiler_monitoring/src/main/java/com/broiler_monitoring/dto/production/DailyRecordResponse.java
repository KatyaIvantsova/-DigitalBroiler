package com.broiler_monitoring.dto.production;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyRecordResponse(
        UUID id,
        UUID flockId,
        LocalDate recordDate,
        int ageDay,
        int mortalityHeads,
        int culledHeads,
        Double feedConsumedKg,
        Double waterConsumedL,
        String comment,
        /** Поголовье на конец суток. */
        int headsAtEnd,
        Instant createdAt,
        Instant updatedAt
) {
}
