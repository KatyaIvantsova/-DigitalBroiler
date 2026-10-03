package com.broiler_monitoring.dto.production;

import com.broiler_monitoring.enumerated.WeighingMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

public record WeighingRequest(
        Instant weighedAt,
        UUID zoneId,
        @NotNull @Positive Integer sampleHeads,
        @NotNull @Positive Double avgWeightG,
        @PositiveOrZero @DecimalMax("100") Double uniformityPct,
        WeighingMethod method
) {
}
