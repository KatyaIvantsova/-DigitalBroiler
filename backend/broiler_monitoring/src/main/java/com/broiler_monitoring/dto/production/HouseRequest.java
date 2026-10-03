package com.broiler_monitoring.dto.production;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record HouseRequest(
        @NotNull UUID siteId,
        @NotBlank @Size(max = 32) String code,
        @NotBlank String name,
        @Positive Double areaM2,
        @Positive Integer capacityHeads,
        Boolean active
) {
}
