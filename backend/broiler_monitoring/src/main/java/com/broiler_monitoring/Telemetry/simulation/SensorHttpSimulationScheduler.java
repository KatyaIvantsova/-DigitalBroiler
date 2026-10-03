package com.broiler_monitoring.Telemetry.simulation;

import com.broiler_monitoring.Telemetry.SensorType;
import com.broiler_monitoring.security.TelemetryApiKeyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "sensor.simulation", name = "enabled", havingValue = "true")
public class SensorHttpSimulationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SensorHttpSimulationScheduler.class);
    private static final double OUTLIER_CHANCE = 0.05;
    private static final List<SimulatedSensor> SENSORS = List.of(
            new SimulatedSensor("TEMP-HOUSE-4-01", SensorType.TEMPERATURE, "C", 32.0, 34.0, 34.8, 35.5),
            new SimulatedSensor("HUM-HOUSE-4-01", SensorType.HUMIDITY, "%", 50.0, 65.0, 44.0, 46.0),
            new SimulatedSensor("AMMONIA-HOUSE-4-01", SensorType.AMMONIA, "ppm", 0.0, 10.0, 12.0, 13.5),
            new SimulatedSensor("FEED-HOUSE-4-01", SensorType.FEED_CONSUMPTION, "kg/h", 35.0, 80.0, 95.0, 110.0),
            new SimulatedSensor("WATER-HOUSE-4-01", SensorType.WATER_FLOW, "l/min", 8.0, 18.0, 22.0, 25.0),
            new SimulatedSensor("LIGHT-01", SensorType.LIGHT, "lux", 25.0, 40.0, 18.0, 22.0),
            new SimulatedSensor("LIGHT-02", SensorType.LIGHT, "lux", 25.0, 40.0, 20.0, 25.0),
            new SimulatedSensor("LIGHT-03", SensorType.LIGHT, "lux", 25.0, 40.0, 22.0, 28.0),
            new SimulatedSensor("LIGHT-04", SensorType.LIGHT, "lux", 25.0, 40.0, 28.0, 35.0),
            new SimulatedSensor("LIGHT-05", SensorType.LIGHT, "lux", 25.0, 40.0, 5.0, 10.0)
    );

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String targetUrl;
    private final String gatewayId;
    private final String apiKey;

    public SensorHttpSimulationScheduler(
            @Value("${sensor.simulation.target-url:http://localhost:8080/api/v1/telemetry/readings}") String targetUrl,
            @Value("${sensor.simulation.gateway-id:GW-FARM-1-HOUSE-4}") String gatewayId,
            @Value("${telemetry.ingest.api-key:}") String apiKey
    ) {
        this.targetUrl = targetUrl;
        this.gatewayId = gatewayId;
        this.apiKey = apiKey;
    }

    @Scheduled(
            fixedDelayString = "${sensor.simulation.interval-ms:600000}",
            initialDelayString = "${sensor.simulation.initial-delay-ms:15000}"
    )
    public void sendReadings() {
        String payload = buildPayload();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (!apiKey.isBlank()) {
            requestBuilder.header(TelemetryApiKeyFilter.HEADER, apiKey);
        }
        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() >= 300) {
                log.warn("Sensor simulation request failed with status {}: {}", response.statusCode(), response.body());
            } else {
                log.info("Sensor simulation sent {} readings", SENSORS.size());
            }
        } catch (IOException exception) {
            log.warn("Sensor simulation cannot reach telemetry API: {}", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Sensor simulation request was interrupted");
        }
    }

    private String buildPayload() {
        Instant measuredAt = Instant.now();
        String readings = SENSORS.stream()
                .map(sensor -> toReadingJson(sensor, measuredAt))
                .collect(Collectors.joining(","));

        return "{\"gatewayId\":\"" + escapeJson(gatewayId) + "\",\"readings\":[" + readings + "]}";
    }

    private String toReadingJson(SimulatedSensor sensor, Instant measuredAt) {
        return "{"
                + "\"sensorCode\":\"" + escapeJson(sensor.code()) + "\","
                + "\"type\":\"" + sensor.type().name() + "\","
                + "\"value\":" + formatValue(sensor.nextValue()) + ","
                + "\"unit\":\"" + escapeJson(sensor.unit()) + "\","
                + "\"measuredAt\":\"" + measuredAt + "\""
                + "}";
    }

    private String formatValue(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private record SimulatedSensor(
            String code,
            SensorType type,
            String unit,
            double min,
            double max,
            double outlierMin,
            double outlierMax
    ) {
        private double nextValue() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            boolean outlier = random.nextDouble() < OUTLIER_CHANCE;
            double minValue = outlier ? outlierMin : min;
            double maxValue = outlier ? outlierMax : max;

            return random.nextDouble(minValue, maxValue);
        }
    }
}
