package com.broiler_monitoring.rules;

import com.broiler_monitoring.Telemetry.InfluxTelemetryPoint;
import com.broiler_monitoring.Telemetry.InfluxTelemetryStorage;
import com.broiler_monitoring.Telemetry.SensorType;
import com.broiler_monitoring.entity.Incident;
import com.broiler_monitoring.enumerated.IncidentPriority;
import com.broiler_monitoring.enumerated.IncidentSource;
import com.broiler_monitoring.enumerated.IncidentStatus;
import com.broiler_monitoring.enumerated.IncidentType;
import com.broiler_monitoring.repository.IncidentHistoryRepository;
import com.broiler_monitoring.repository.IncidentRepository;
import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3-03…S3-07: цикл правил по реальным показаниям в InfluxDB — создание, дедупликация, повышение,
 * автозакрытие и «нет данных». Нормы берутся из справочника для возраста и кросса активной партии.
 */
class RuleEvaluationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    RuleEvaluationService evaluation;
    @Autowired
    InfluxTelemetryStorage influx;
    @Autowired
    IncidentRepository incidents;
    @Autowired
    IncidentHistoryRepository history;

    /** Птичник с активной партией Ross 308 возрастом 10 дней и датчиком нужного типа. */
    private record Setup(String houseId, String flockId, UUID sensorId, String sensorCode) {
    }

    private Setup setup(Api admin, SensorType type, String unit) throws Exception {
        String houseId = admin.newHouse();
        LocalDate placedAt = LocalDate.now(ZoneId.of("Europe/Samara")).minusDays(10);
        String flockId = admin.create("/api/v1/flocks", """
                {"houseId":"%s","breedCode":"ROSS_308","placedAt":"%s","placedHeads":20000}
                """.formatted(houseId, placedAt));
        String code = type + "-T-" + UUID.randomUUID().toString().substring(0, 8);
        String sensorId = admin.create("/api/v1/sensors", """
                {"code":"%s","name":"Тестовый датчик","type":"%s","farm":"Ферма 1","building":"Тест","unit":"%s"}
                """.formatted(code, type, unit));
        admin.patch("/api/v1/sensors/" + sensorId + "/location", "{\"houseId\":\"" + houseId + "\"}").andExpect(status().isOk());
        return new Setup(houseId, flockId, UUID.fromString(sensorId), code);
    }

    private void write(Setup setup, SensorType type, String unit, Instant from, int minutes, double value) {
        List<InfluxTelemetryPoint> points = new ArrayList<>();
        for (int minute = 0; minute <= minutes; minute += 5) {
            Instant at = from.plus(Duration.ofMinutes(minute));
            points.add(new InfluxTelemetryPoint(setup.sensorId(), setup.sensorCode(), type, "Ферма 1", "Тест", "GW-TEST", value, unit, at, at));
        }
        influx.write(points);
    }

    private List<Incident> openFor(Setup setup) {
        return incidents.findAllOpen().stream().filter(incident -> setup.sensorId().equals(incident.getSensorId())).toList();
    }

    @Test
    void temperatureRuleRaisesDeduplicatesEscalatesAndAutoResolves() throws Exception {
        Api admin = Api.admin(mvc);
        Setup setup = setup(admin, SensorType.TEMPERATURE, "C");
        // Ross 308, день 10: норма 24,7–26,7 °C (norms-v1.csv)
        Instant t0 = Instant.now().minus(Duration.ofHours(3)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        write(setup, SensorType.TEMPERATURE, "C", t0, 40, 27.2);
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(40)));
        List<Incident> open = openFor(setup);
        assertThat(open).hasSize(1);
        Incident incident = open.getFirst();
        assertThat(incident.getSource()).isEqualTo(IncidentSource.SYSTEM);
        assertThat(incident.getType()).isEqualTo(IncidentType.MICROCLIMATE);
        assertThat(incident.getPriority()).isEqualTo(IncidentPriority.HIGH);
        assertThat(incident.getRuleCode()).isEqualTo("MICROCLIMATE_TEMPERATURE");
        assertThat(incident.getFlockId()).hasToString(setup.flockId());
        assertThat(incident.getHouseId()).hasToString(setup.houseId());
        assertThat(incident.getDescription()).contains("27,2").contains("24,7–26,7").contains("день 10").contains("ROSS_308");

        // Повторный цикл не создаёт дубль
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(41)));
        assertThat(openFor(setup)).hasSize(1);

        // Отклонение выросло (> max + 1 °C 15 минут) — приоритет поднимается, инцидент тот же
        write(setup, SensorType.TEMPERATURE, "C", t0.plus(Duration.ofMinutes(45)), 20, 28.5);
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(65)));
        assertThat(openFor(setup)).singleElement().satisfies(escalated -> {
            assertThat(escalated.getId()).isEqualTo(incident.getId());
            assertThat(escalated.getPriority()).isEqualTo(IncidentPriority.CRITICAL);
            assertThat(escalated.getDescription()).contains("28,5 °C");
        });

        // В норме 15+ минут — инцидент, который никто не взял, закрывается автоматически
        write(setup, SensorType.TEMPERATURE, "C", t0.plus(Duration.ofMinutes(70)), 20, 25.5);
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(90)));
        assertThat(openFor(setup)).isEmpty();
        Incident resolved = incidents.findById(incident.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(history.findByIncidentIdOrderByCreatedAtAsc(incident.getId()))
                .extracting(entry -> entry.getEventType())
                .containsExactly("CREATED", "ESCALATED", "STATUS_CHANGED");
        admin.get("/api/v1/audit?entityType=INCIDENT&entityId=" + incident.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actorName").value("Система"));
    }

    @Test
    void incidentInProgressIsNotClosedByEngine() throws Exception {
        Api admin = Api.admin(mvc);
        Setup setup = setup(admin, SensorType.CO2, "ppm");
        Instant t0 = Instant.now().minus(Duration.ofHours(3)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        write(setup, SensorType.CO2, "ppm", t0, 40, 2800);
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(40)));
        Incident incident = openFor(setup).getFirst();
        incidents.findById(incident.getId()).ifPresent(found -> {
            found.setStatus(IncidentStatus.IN_PROGRESS);
            incidents.save(found);
        });

        write(setup, SensorType.CO2, "ppm", t0.plus(Duration.ofMinutes(45)), 20, 1500);
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(65)));
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(66)));
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(history.findByIncidentIdOrderByCreatedAtAsc(incident.getId()))
                .filteredOn(entry -> "BACK_TO_NORMAL".equals(entry.getEventType()))
                .hasSize(1);
    }

    @Test
    void sensorWithoutDataRaisesNoDataAndClearsOnNewReading() throws Exception {
        Api admin = Api.admin(mvc);
        Setup setup = setup(admin, SensorType.HUMIDITY, "%");
        Instant t0 = Instant.now().minus(Duration.ofHours(2)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        write(setup, SensorType.HUMIDITY, "%", t0, 10, 60);

        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(60)));
        assertThat(openFor(setup)).singleElement().satisfies(incident -> {
            assertThat(incident.getType()).isEqualTo(IncidentType.SENSOR_NO_DATA);
            assertThat(incident.getRuleCode()).isEqualTo("SENSOR_NO_DATA");
            assertThat(incident.getDescription()).contains(setup.sensorCode()).contains("50 мин");
        });

        write(setup, SensorType.HUMIDITY, "%", t0.plus(Duration.ofMinutes(61)), 0, 60);
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(62)));
        assertThat(openFor(setup)).isEmpty();
    }

    @Test
    void houseWithoutActiveFlockChecksOnlyNoData() throws Exception {
        Api admin = Api.admin(mvc);
        Setup setup = setup(admin, SensorType.AMMONIA, "ppm");
        admin.post("/api/v1/flocks/" + setup.flockId() + "/close", """
                {"closedAt":"%s","shippedHeads":19000,"shippedLiveWeightKg":45000}
                """.formatted(LocalDate.now(ZoneId.of("Europe/Samara")))).andExpect(status().isOk());
        Instant t0 = Instant.now().minus(Duration.ofHours(3)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        write(setup, SensorType.AMMONIA, "ppm", t0, 40, 30);
        evaluation.evaluateAll(t0.plus(Duration.ofMinutes(40)));
        assertThat(openFor(setup)).isEmpty();
    }

    @Test
    void ruleThresholdsAreEditableAndApplyWithoutRestart() throws Exception {
        Api admin = Api.admin(mvc);
        Setup setup = setup(admin, SensorType.TEMPERATURE, "C");
        admin.put("/api/v1/rules/MICROCLIMATE_TEMPERATURE", """
                {"warnMinutes":30,"criticalDelta":1.0,"criticalMinutes":15,"clearMinutes":15,"enabled":false}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        try {
            Instant t0 = Instant.now().minus(Duration.ofHours(3)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            write(setup, SensorType.TEMPERATURE, "C", t0, 40, 35);
            evaluation.evaluateAll(t0.plus(Duration.ofMinutes(40)));
            assertThat(openFor(setup)).isEmpty();
        } finally {
            admin.put("/api/v1/rules/MICROCLIMATE_TEMPERATURE", """
                    {"warnMinutes":30,"criticalDelta":1.0,"criticalMinutes":15,"clearMinutes":15,"enabled":true}
                    """).andExpect(status().isOk());
        }
        admin.get("/api/v1/audit?entityType=RULE&entityId=MICROCLIMATE_TEMPERATURE").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changes").value(org.hamcrest.Matchers.containsString("Включено")));
    }
}
