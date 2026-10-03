package com.broiler_monitoring.integration.mqtt;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** S4-06: показания из MQTT-брокера (Mosquitto) попадают в InfluxDB через общий приём телеметрии. */
class MqttTelemetryAdapterIntegrationTest extends AbstractIntegrationTest {

    static final GenericContainer<?> MOSQUITTO = new GenericContainer<>("eclipse-mosquitto:2")
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .withExposedPorts(1883)
            .waitingFor(Wait.forListeningPort());

    static {
        MOSQUITTO.start();
    }

    @DynamicPropertySource
    static void mqtt(DynamicPropertyRegistry registry) {
        registry.add("mqtt.enabled", () -> "true");
        registry.add("mqtt.url", MqttTelemetryAdapterIntegrationTest::brokerUrl);
        registry.add("mqtt.flush-interval-ms", () -> "300");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MqttTelemetryAdapter adapter;

    @Test
    void readingsFromBrokerAreStored() throws Exception {
        Api admin = Api.admin(mvc);
        String code = "MQTT-TEMP-" + UUID.randomUUID().toString().substring(0, 6);
        admin.create("/api/v1/sensors", """
                {"code":"%s","name":"Датчик MQTT","type":"TEMPERATURE","farm":"Ферма 1","building":"Тест","unit":"C"}
                """.formatted(code));
        waitUntil(adapter::isConnected);

        Instant measured = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(60);
        MqttClient publisher = new MqttClient(brokerUrl(), "test-publisher", new MemoryPersistence());
        publisher.connect();
        publish(publisher, "broiler/FARM-1/T/" + code,
                "{\"type\":\"TEMPERATURE\",\"value\":26.4,\"unit\":\"C\",\"measuredAt\":\"%s\"}".formatted(measured));
        publish(publisher, "broiler/FARM-1/T/" + code, "27,1");
        publish(publisher, "broiler/FARM-1/T/UNKNOWN-SENSOR", "1");
        publish(publisher, "broiler/FARM-1/T/" + code, "{\"type\":\"HUMIDITY\",\"value\":60}");
        publisher.disconnect();
        publisher.close();

        waitUntil(() -> adapter.savedCount() >= 2 && adapter.rejectedCount() >= 2);
        admin.get("/api/v1/telemetry/readings?sensorCode=" + code + "&limit=10")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].value", equalTo(26.4)));
        assertThat(adapter.savedCount()).isEqualTo(2);
    }

    private static String brokerUrl() {
        return "tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(1883);
    }

    private static void publish(MqttClient client, String topic, String payload) throws Exception {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        client.publish(topic, message);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Условие не выполнилось за 15 с");
            }
            Thread.sleep(100);
        }
    }
}
