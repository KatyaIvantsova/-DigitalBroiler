package com.broiler_monitoring.dto.norms;

import jakarta.validation.constraints.NotBlank;

/** Новая версия нормы: границы и обоснование правки (обязательно — попадает в журнал). */
public record NormUpdateRequest(
        Double minValue,
        Double targetValue,
        Double maxValue,
        String source,
        @NotBlank(message = "укажите причину изменения") String comment
) {
}
