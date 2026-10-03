package com.broiler_monitoring.enumerated;

public enum IncidentType {
    MICROCLIMATE("Микроклимат"),
    SANITATION("Санитария"),
    FLOCK_HEALTH("Падеж и состояние стада"),
    FEEDING("Кормление"),
    WATER_SUPPLY("Водоснабжение"),
    PRODUCTION_METRICS("Производственные показатели"),

    LIGHTING_ILLUMINANCE_LOW("Освещенность ниже нормы"),
    LIGHTING_ILLUMINANCE_HIGH("Освещенность выше нормы"),
    LIGHTING_UNIFORMITY_VIOLATION("Нарушение равномерности освещения"),
    LIGHTING_SYSTEM_HEALTH_WARNING("Ухудшение состояния системы освещения"),
    LIGHTING_SYSTEM_HEALTH_CRITICAL("Критическое состояние системы освещения"),
    LIGHTING_SCHEDULE_DEVIATION("Отклонение от расписания освещения"),
    LIGHTING_DARK_PERIOD_VIOLATION("Нарушение темного периода"),
    LIGHTING_CONTROLLER_FAILURE("Сбой контроллера освещения"),
    LIGHTING_CONTINUOUS_LIGHT("Свет включен 24/7"),
    LIGHTING_CONTINUOUS_DARK("Постоянная темнота"),
    LIGHTING_MISSING_EVENTS("Потеря событий управления"),

    SENSOR_NO_DATA("Нет данных от датчика"),

    OTHER("Прочее");

    private final String displayName;

    IncidentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
