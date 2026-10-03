package com.broiler_monitoring.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Шаблон интеграционного теста (S1-13): настоящие Postgres и InfluxDB в Docker через Testcontainers.
 * Контейнеры общие для всех наследников и поднимаются один раз на прогон.
 * Наследуйте этот класс и пишите тест как обычный @SpringBootTest с MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "integration-test-password";
    public static final String INGEST_API_KEY = "integration-test-ingest-key";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static final GenericContainer<?> INFLUX = new GenericContainer<>("influxdb:1.8")
            .withEnv("INFLUXDB_DB", "broiler_telemetry")
            .withExposedPorts(8086)
            .waitingFor(Wait.forHttp("/ping").forStatusCode(204));

    static {
        POSTGRES.start();
        INFLUX.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("telemetry.influx.url",
                () -> "http://" + INFLUX.getHost() + ":" + INFLUX.getMappedPort(8086));
        registry.add("telemetry.influx.database", () -> "broiler_telemetry");
        registry.add("telemetry.ingest.api-key", () -> INGEST_API_KEY);
        registry.add("sensor.simulation.enabled", () -> "false");
        registry.add("auth.jwt.secret", () -> "integration-test-secret-at-least-32-bytes-long");
        registry.add("auth.bootstrap-admin.username", () -> ADMIN_USERNAME);
        registry.add("auth.bootstrap-admin.password", () -> ADMIN_PASSWORD);
        registry.add("incident.attachments.s3.access-key", () -> "test");
        registry.add("incident.attachments.s3.secret-key", () -> "test");
    }
}
