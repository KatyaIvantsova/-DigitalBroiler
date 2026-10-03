package com.broiler_monitoring.dto;

import java.time.LocalDateTime;

/**
 * Частичное обновление задачи: применяются только переданные (не null) поля.
 */
public record TaskPatchRequest(
        String nameTask,
        String descriptionTask,
        String nameIndicator,
        String valueIndicator,
        String measure,
        String priority,
        String responsible,
        String status,
        LocalDateTime termTask
) {
}
