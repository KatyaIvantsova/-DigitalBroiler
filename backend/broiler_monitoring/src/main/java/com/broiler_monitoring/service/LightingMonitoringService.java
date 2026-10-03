package com.broiler_monitoring.service;

import com.broiler_monitoring.Telemetry.InfluxTelemetryPoint;
import com.broiler_monitoring.Telemetry.InfluxTelemetryStorage;
import com.broiler_monitoring.Telemetry.Sensor;
import com.broiler_monitoring.Telemetry.SensorRepository;
import com.broiler_monitoring.Telemetry.SensorType;
import com.broiler_monitoring.entity.House;
import com.broiler_monitoring.repository.HouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Равномерность освещения по птичнику: разброс последних показаний датчиков освещённости, % от среднего.
 * Освещённость и программа освещения проверяются движком правил по нормам для возраста партии
 * (rules.RuleEvaluationService, S3-06) — константы 25–40 лк и заглушка расписания удалены.
 */
@Service
public class LightingMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(LightingMonitoringService.class);
    /** Показания старше этого срока в расчёт не берутся. */
    private static final Duration FRESH = Duration.ofMinutes(30);
    /** Ниже этой освещённости свет выключен (тёмный период) — равномерность не считается. */
    private static final double LIGHT_ON_LUX = 1.0;

    private final boolean enabled;
    private final SensorRepository sensors;
    private final HouseRepository houses;
    private final InfluxTelemetryStorage influxStorage;
    private final Clock clock;

    public LightingMonitoringService(@Value("${rules.evaluation.enabled:true}") boolean enabled,
                                     SensorRepository sensors, HouseRepository houses,
                                     InfluxTelemetryStorage influxStorage, Clock clock) {
        this.enabled = enabled;
        this.sensors = sensors;
        this.houses = houses;
        this.influxStorage = influxStorage;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1200000, initialDelay = 120000) // каждые 20 минут
    public void checkUniformity() {
        if (!enabled) {
            return;
        }
        for (House house : houses.findAll()) {
            if (!house.isActive()) {
                continue;
            }
            try {
                checkUniformity(house);
            } catch (Exception exception) {
                log.warn("Равномерность освещения птичника {} не рассчитана: {}", house.getCode(), exception.getMessage());
            }
        }
    }

    private void checkUniformity(House house) {
        Instant freshFrom = clock.instant().minus(FRESH);
        List<Double> values = new ArrayList<>();
        for (Sensor sensor : sensors.findAll()) {
            if (!house.getId().equals(sensor.getHouseId()) || sensor.getType() != SensorType.LIGHT || !Boolean.TRUE.equals(sensor.getActive())) {
                continue;
            }
            List<InfluxTelemetryPoint> latest = influxStorage.findLatest(sensor.getCode(), 1);
            if (!latest.isEmpty() && latest.getFirst().value() != null && !latest.getFirst().measuredAt().isBefore(freshFrom)) {
                values.add(latest.getFirst().value());
            }
        }
        if (values.size() < 2 || values.stream().allMatch(value -> value < LIGHT_ON_LUX)) {
            return;
        }
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double unevenness = avg == 0 ? 0 : (max - min) / avg * 100;
        influxStorage.saveUniformity(house.getCode(), min, max, avg, unevenness, values.size());
    }
}
