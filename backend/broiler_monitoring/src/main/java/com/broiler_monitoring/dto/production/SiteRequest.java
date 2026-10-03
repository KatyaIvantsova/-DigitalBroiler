package com.broiler_monitoring.dto.production;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SiteRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank String name,
        String address,
        String timezone
) {
}
