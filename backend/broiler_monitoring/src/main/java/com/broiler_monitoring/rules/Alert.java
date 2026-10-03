package com.broiler_monitoring.rules;

import com.broiler_monitoring.enumerated.IncidentType;

import java.util.UUID;

/** Сработавшее правило: из него создаётся или повышается инцидент. */
public record Alert(
        String dedupKey,
        String ruleCode,
        IncidentType type,
        RuleEngine.Level level,
        String title,
        String description,
        UUID houseId,
        String houseName,
        UUID zoneId,
        String zoneName,
        UUID flockId,
        UUID sensorId
) {
}
