package com.broiler_monitoring.dto.production;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record DailyRecordRequest(
        @NotNull LocalDate recordDate,
        @NotNull @PositiveOrZero Integer mortalityHeads,
        @NotNull @PositiveOrZero Integer culledHeads,
        @PositiveOrZero Double feedConsumedKg,
        @PositiveOrZero Double waterConsumedL,
        String comment
) {
}
