package com.broiler_monitoring.enumerated;

/**
 * Роль доступа пользователя. Матрица прав по ролям — задача S2-04,
 * в спринте 1 любая роль даёт доступ ко всему API.
 */
public enum UserRole {
    OPERATOR,
    TECHNOLOGIST,
    VETERINARIAN,
    MANAGER,
    ADMIN
}
