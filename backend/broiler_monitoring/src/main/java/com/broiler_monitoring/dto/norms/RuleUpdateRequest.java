package com.broiler_monitoring.dto.norms;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Пороги правила движка: сколько минут держится отклонение, критичная дельта, включено ли правило. */
public record RuleUpdateRequest(
        @NotNull @PositiveOrZero @Max(1440) Integer warnMinutes,
        @PositiveOrZero Double criticalDelta,
        @NotNull @PositiveOrZero @Max(1440) Integer criticalMinutes,
        @NotNull @PositiveOrZero @Max(1440) Integer clearMinutes,
        @NotNull Boolean enabled
) {
}
