package com.broiler_monitoring.enumerated;

/**
 * Роль доступа пользователя. Матрица прав по ролям — docs/sprint2/04-roles-matrix.md,
 * правила на уровне URL — SecurityConfig, на уровне данных (назначение на птичники) — AccessService.
 */
public enum UserRole {
    OPERATOR,
    TECHNOLOGIST,
    VETERINARIAN,
    MANAGER,
    ADMIN
}
