package com.broiler_monitoring.dto.production;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record CloseFlockRequest(
        @NotNull LocalDate closedAt,
        @NotNull @PositiveOrZero Integer shippedHeads,
        @NotNull @PositiveOrZero Double shippedLiveWeightKg
) {
}
