package com.broiler_monitoring.integration.mqtt;

import com.broiler_monitoring.Telemetry.SensorType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Разбор сообщения шлюза (docs/sprint2/09-integration-protocol.md п. 2). Топик {@code broiler/<site>/<house>/<sensorCode>},
 * полезная нагрузка — JSON {@code {"type","value","unit","measuredAt"}} или просто число: тогда тип и единица
 * берутся из справочника датчиков, время — момент приёма.
 */
public final class MqttPayloadParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MqttPayloadParser() {
    }

    /** type и unit — null, если шлюз их не прислал. */
    public record Reading(String gatewayId, String sensorCode, SensorType type, Double value, String unit, Instant measuredAt) {
    }

    public static Reading parse(String topic, String payload, Instant receivedAt) {
        String[] parts = topic.split("/");
        if (parts.length != 4 || !"broiler".equals(parts[0]) || parts[3].isBlank()) {
            throw new IllegalArgumentException("Топик «%s» не по формату broiler/<площадка>/<птичник>/<код датчика>".formatted(topic));
        }
        String gateway = "MQTT:" + parts[1] + "/" + parts[2];
        String sensorCode = parts[3];
        String body = payload == null ? "" : payload.trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Пустое сообщение в топике " + topic);
        }
        if (!body.startsWith("{")) {
            return new Reading(gateway, sensorCode, null, number(body), null, receivedAt);
        }
        JsonNode node;
        try {
            node = JSON.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Сообщение в топике %s — не JSON".formatted(topic));
        }
        JsonNode value = node.get("value");
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Нет значения value в топике " + topic);
        }
        Double parsed = value.isNumber() ? value.asDouble() : number(value.asText());
        SensorType type = null;
        if (node.hasNonNull("type")) {
            try {
                type = SensorType.valueOf(node.get("type").asText().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Неизвестный тип «%s» в топике %s".formatted(node.get("type").asText(), topic));
            }
        }
        String unit = node.hasNonNull("unit") ? node.get("unit").asText() : null;
        Instant measuredAt = receivedAt;
        if (node.hasNonNull("measuredAt")) {
            try {
                measuredAt = Instant.parse(node.get("measuredAt").asText());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Время «%s» не в формате ISO-8601 с Z".formatted(node.get("measuredAt").asText()));
            }
        }
        return new Reading(gateway, sensorCode, type, parsed, unit, measuredAt);
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Значение «%s» — не число".formatted(text));
        }
    }
}
