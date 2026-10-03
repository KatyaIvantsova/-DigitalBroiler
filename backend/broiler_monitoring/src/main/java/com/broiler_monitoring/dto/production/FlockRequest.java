package com.broiler_monitoring.dto.production;

import com.broiler_monitoring.enumerated.FlockSex;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Посадка партии. code можно не задавать — тогда он строится как ГГГГ-ММ-<код птичника>. */
public record FlockRequest(
        @NotNull UUID houseId,
        @Size(max = 32) String code,
        @NotBlank String breedCode,
        @NotNull LocalDate placedAt,
        @NotNull @Positive Integer placedHeads,
        @Positive Double placedAvgWeightG,
        FlockSex sex,
        String hatchery,
        @Positive @Max(120) Integer targetAgeDays
) {
}
