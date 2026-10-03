package com.broiler_monitoring.integration.mqtt;

import com.broiler_monitoring.Telemetry.SensorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S4-06: формат сообщений шлюза по docs/sprint2/09-integration-protocol.md п. 2. */
class MqttPayloadParserTest {

    private static final Instant RECEIVED = Instant.parse("2026-10-20T08:15:30Z");

    @Test
    void parsesJsonPayload() {
        MqttPayloadParser.Reading reading = MqttPayloadParser.parse("broiler/FARM-1/1-04/TEMP-HOUSE-4-01",
                "{\"type\":\"TEMPERATURE\",\"value\":26.4,\"unit\":\"C\",\"measuredAt\":\"2026-10-20T08:15:00Z\"}", RECEIVED);
        assertThat(reading.gatewayId()).isEqualTo("MQTT:FARM-1/1-04");
        assertThat(reading.sensorCode()).isEqualTo("TEMP-HOUSE-4-01");
        assertThat(reading.type()).isEqualTo(SensorType.TEMPERATURE);
        assertThat(reading.value()).isEqualTo(26.4);
        assertThat(reading.unit()).isEqualTo("C");
        assertThat(reading.measuredAt()).isEqualTo(Instant.parse("2026-10-20T08:15:00Z"));
    }

    @Test
    void plainNumberTakesReceiveTimeAndLeavesTypeToRegistry() {
        MqttPayloadParser.Reading reading = MqttPayloadParser.parse("broiler/FARM-1/1-04/HUM-01", " 61,5 ", RECEIVED);
        assertThat(reading.value()).isEqualTo(61.5);
        assertThat(reading.type()).isNull();
        assertThat(reading.unit()).isNull();
        assertThat(reading.measuredAt()).isEqualTo(RECEIVED);
    }

    @Test
    void rejectsMalformedMessages() {
        assertThatThrownBy(() -> MqttPayloadParser.parse("broiler/FARM-1/TEMP-01", "1", RECEIVED))
                .hasMessageContaining("не по формату");
        assertThatThrownBy(() -> MqttPayloadParser.parse("broiler/F/H/S", "abc", RECEIVED))
                .hasMessageContaining("не число");
        assertThatThrownBy(() -> MqttPayloadParser.parse("broiler/F/H/S", "{\"value\":1,\"type\":\"RADIATION\"}", RECEIVED))
                .hasMessageContaining("Неизвестный тип");
        assertThatThrownBy(() -> MqttPayloadParser.parse("broiler/F/H/S", "{\"value\":1,\"measuredAt\":\"вчера\"}", RECEIVED))
                .hasMessageContaining("ISO-8601");
        assertThatThrownBy(() -> MqttPayloadParser.parse("broiler/F/H/S", "{\"unit\":\"C\"}", RECEIVED))
                .hasMessageContaining("Нет значения");
    }
}
