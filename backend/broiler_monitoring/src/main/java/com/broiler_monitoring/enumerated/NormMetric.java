package com.broiler_monitoring.enumerated;

/** Показатель справочника норм. */
public enum NormMetric {
    TEMPERATURE("Температура"),
    HUMIDITY("Влажность"),
    CO2("CO2"),
    AMMONIA("Аммиак"),
    LIGHT_INTENSITY("Освещённость"),
    LIGHT_HOURS("Продолжительность светового дня"),
    BODY_WEIGHT("Живой вес"),
    FEED_INTAKE("Потребление корма"),
    WATER_INTAKE("Потребление воды"),
    FCR("Конверсия корма (нарастающим)"),
    MORTALITY("Падёж за сутки"),
    UNIFORMITY("Однородность");

    private final String displayName;

    NormMetric(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
