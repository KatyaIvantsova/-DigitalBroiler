package com.broiler_monitoring.dto.production;

import java.util.List;

/**
 * Итог импорта учёта (S4-07). {@code applied = false} — в файле есть ошибки, ничего не сохранено;
 * {@code errors} — строки с номером и причиной.
 */
public record FlockImportResult(boolean applied, int rows, int created, int updated, int unchanged, int weighings,
                                List<String> errors) {
}
