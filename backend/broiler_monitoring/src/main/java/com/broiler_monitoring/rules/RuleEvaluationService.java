package com.broiler_monitoring.rules;

import com.broiler_monitoring.Telemetry.InfluxTelemetryPoint;
import com.broiler_monitoring.Telemetry.InfluxTelemetryStorage;
import com.broiler_monitoring.Telemetry.Sensor;
import com.broiler_monitoring.Telemetry.SensorRepository;
import com.broiler_monitoring.Telemetry.SensorType;
import com.broiler_monitoring.entity.Flock;
import com.broiler_monitoring.entity.House;
import com.broiler_monitoring.entity.Norm;
import com.broiler_monitoring.entity.Rule;
import com.broiler_monitoring.entity.Zone;
import com.broiler_monitoring.enumerated.IncidentType;
import com.broiler_monitoring.enumerated.NormMetric;
import com.broiler_monitoring.repository.HouseRepository;
import com.broiler_monitoring.repository.RuleRepository;
import com.broiler_monitoring.repository.ZoneRepository;
import com.broiler_monitoring.service.FlockService;
import com.broiler_monitoring.service.NormService;
import com.broiler_monitoring.service.StructureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Цикл движка правил (S3-03…S3-07): раз в минуту сравнивает показания каждого датчика, привязанного к птичнику,
 * с нормой для возраста и кросса активной партии; раз в час проверяет программу освещения за сутки.
 * Пороги и нормы читаются из БД на каждом цикле — их правка применяется без перезапуска.
 */
@Service
public class RuleEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluationService.class);
    public static final String NO_DATA = "SENSOR_NO_DATA";
    public static final String LIGHTING_PROGRAM = "LIGHTING_PROGRAM";
    /** Ниже этой освещённости считаем, что свет выключен (тёмный период), и правило освещённости не применяем. */
    static final double LIGHT_ON_LUX = 1.0;
    /** Без показаний дольше этого срока интервал не учитывается в расчёте светового дня. */
    static final Duration LIGHT_MAX_GAP = Duration.ofMinutes(60);
    /** Для расчёта светового дня нужно покрытие данными хотя бы 20 часов из 24. */
    static final long LIGHT_MIN_COVERAGE_MINUTES = 20 * 60;

    private final boolean enabled;
    private final SensorRepository sensors;
    private final HouseRepository houses;
    private final ZoneRepository zones;
    private final RuleRepository rules;
    private final FlockService flocks;
    private final NormService norms;
    private final StructureService structure;
    private final InfluxTelemetryStorage influx;
    private final IncidentAutomationService automation;
    private final Clock clock;
    private final Map<UUID, Instant> lightProgramCheckedAt = new HashMap<>();

    public RuleEvaluationService(@Value("${rules.evaluation.enabled:true}") boolean enabled,
                                 SensorRepository sensors, HouseRepository houses, ZoneRepository zones,
                                 RuleRepository rules, FlockService flocks, NormService norms, StructureService structure,
                                 InfluxTelemetryStorage influx, IncidentAutomationService automation, Clock clock) {
        this.enabled = enabled;
        this.sensors = sensors;
        this.houses = houses;
        this.zones = zones;
        this.rules = rules;
        this.flocks = flocks;
        this.norms = norms;
        this.structure = structure;
        this.influx = influx;
        this.automation = automation;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${rules.evaluation.interval-ms:60000}", initialDelayString = "${rules.evaluation.initial-delay-ms:60000}")
    public void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            Summary summary = evaluateAll(clock.instant());
            log.debug("Цикл правил: {}", summary);
        } catch (Exception exception) {
            log.error("Цикл движка правил завершился ошибкой", exception);
        }
    }

    /** Итог цикла: сколько датчиков проверено и сколько инцидентов создано, повышено или закрыто. */
    public record Summary(int sensors, int raised, int cleared, int skipped) {
    }

    public synchronized Summary evaluateAll(Instant now) {
        Map<String, Rule> enabledRules = new HashMap<>();
        rules.findByEnabledTrue().forEach(rule -> enabledRules.put(rule.getCode(), rule));
        Counter counter = new Counter();
        Map<UUID, Context> contexts = new HashMap<>();
        for (Sensor sensor : sensors.findAll()) {
            if (!Boolean.TRUE.equals(sensor.getActive()) || sensor.getHouseId() == null) {
                continue;
            }
            Context context = contexts.computeIfAbsent(sensor.getHouseId(), this::context);
            if (context.house() == null) {
                continue;
            }
            counter.sensors++;
            try {
                evaluateSensor(sensor, context, enabledRules, now, counter);
            } catch (Exception exception) {
                counter.skipped++;
                log.warn("Правила по датчику {} не проверены: {}", sensor.getCode(), exception.getMessage());
            }
        }
        Rule program = enabledRules.get(LIGHTING_PROGRAM);
        if (program != null) {
            for (Context context : contexts.values()) {
                if (context.house() != null) {
                    evaluateLightingProgram(program, context, now, counter);
                }
            }
        }
        return new Summary(counter.sensors, counter.raised, counter.cleared, counter.skipped);
    }

    /** Принудительно пересчитать программу освещения на следующем цикле (после правки норм). */
    public synchronized void resetLightingProgramSchedule() {
        lightProgramCheckedAt.clear();
    }

    private void evaluateSensor(Sensor sensor, Context context, Map<String, Rule> enabledRules, Instant now, Counter counter) {
        List<Rule> sensorRules = enabledRules.values().stream()
                .filter(rule -> rule.getSensorType() == sensor.getType() && rule.getMetric() != null && !LIGHTING_PROGRAM.equals(rule.getCode()))
                .toList();
        int lookback = sensorRules.stream()
                .mapToInt(rule -> Math.max(rule.getWarnMinutes(), Math.max(rule.getCriticalMinutes(), rule.getClearMinutes())))
                .max().orElse(0) + 60;
        Rule noData = enabledRules.get(NO_DATA);
        if (noData != null) {
            lookback = Math.max(lookback, noData.getWarnMinutes() + 30);
        }
        List<RuleEngine.Reading> readings = influx.findByPeriod(sensor.getCode(), now.minus(Duration.ofMinutes(lookback)), now).stream()
                .filter(point -> point.value() != null)
                .map(point -> new RuleEngine.Reading(point.measuredAt(), point.value()))
                .toList();

        if (noData != null) {
            evaluateNoData(noData, sensor, context, readings, now, counter);
        }
        if (context.flock() == null) {
            return; // пустой птичник: правила по возрасту не проверяются (spec, п. 2)
        }
        for (Rule rule : sensorRules) {
            Optional<Norm> norm = norms.find(rule.getMetric(), context.flock().getBreedCode(), context.ageDay());
            if (norm.isEmpty()) {
                continue;
            }
            List<RuleEngine.Reading> applicable = sensor.getType() == SensorType.LIGHT
                    ? readings.stream().filter(reading -> reading.value() >= LIGHT_ON_LUX).toList()
                    : readings;
            RuleEngine.Thresholds thresholds = new RuleEngine.Thresholds(norm.get().getMinValue(), norm.get().getMaxValue(),
                    rule.getCriticalDelta(), rule.getWarnMinutes(), rule.getCriticalMinutes(), rule.getClearMinutes());
            RuleEngine.Level level = RuleEngine.evaluate(applicable, thresholds, now);
            String key = rule.getCode() + ":" + sensor.getId();
            switch (level) {
                case WARNING, CRITICAL -> {
                    double value = applicable.stream().max(java.util.Comparator.comparing(RuleEngine.Reading::at)).orElseThrow().value();
                    automation.raise(alert(rule, key, typeFor(rule, norm.get(), value), level, sensor, context,
                            describe(rule, norm.get(), value, context, level)));
                    counter.raised++;
                }
                case NORMAL -> automation.clear(key, "%s вернулась в норму".formatted(rule.getMetric().getDisplayName()))
                        .ifPresent(incident -> counter.cleared++);
                case UNCHANGED -> {
                }
            }
        }
    }

    private void evaluateNoData(Rule rule, Sensor sensor, Context context, List<RuleEngine.Reading> readings, Instant now, Counter counter) {
        String key = NO_DATA + ":" + sensor.getId();
        Instant last;
        if (!readings.isEmpty()) {
            last = readings.stream().map(RuleEngine.Reading::at).max(Instant::compareTo).orElseThrow();
        } else {
            // датчик, который ни разу не присылал данных, ещё не подключён — инцидент не нужен
            List<InfluxTelemetryPoint> latest = influx.findLatest(sensor.getCode(), 1);
            if (latest.isEmpty()) {
                return;
            }
            last = latest.getFirst().measuredAt();
        }
        if (last.isBefore(now.minus(Duration.ofMinutes(rule.getWarnMinutes())))) {
            long minutes = Duration.between(last, now).toMinutes();
            automation.raise(alert(rule, key, IncidentType.SENSOR_NO_DATA, RuleEngine.Level.WARNING, sensor, context,
                    "Датчик %s (%s) не присылает данные %s".formatted(sensor.getCode(), sensor.getName(), formatMinutes(minutes))));
            counter.raised++;
        } else {
            automation.clear(key, "Данные от датчика снова поступают").ifPresent(incident -> counter.cleared++);
        }
    }

    /**
     * Программа освещения (S3-06): часы света за последние 24 часа по датчикам освещённости птичника
     * против нормы LIGHT_HOURS. Проверяется раз в час.
     */
    private void evaluateLightingProgram(Rule rule, Context context, Instant now, Counter counter) {
        UUID houseId = context.house().getId();
        Instant checked = lightProgramCheckedAt.get(houseId);
        if (checked != null && checked.isAfter(now.minus(Duration.ofMinutes(59)))) {
            return;
        }
        lightProgramCheckedAt.put(houseId, now);
        if (context.flock() == null) {
            return;
        }
        Optional<Norm> norm = norms.find(NormMetric.LIGHT_HOURS, context.flock().getBreedCode(), context.ageDay());
        if (norm.isEmpty()) {
            return;
        }
        Instant from = now.minus(Duration.ofHours(24));
        List<Double> lightHours = new ArrayList<>();
        for (Sensor sensor : sensors.findAll()) {
            if (!houseId.equals(sensor.getHouseId()) || sensor.getType() != SensorType.LIGHT || !Boolean.TRUE.equals(sensor.getActive())) {
                continue;
            }
            List<RuleEngine.Reading> readings = influx.findByPeriod(sensor.getCode(), from.minus(LIGHT_MAX_GAP), now).stream()
                    .filter(point -> point.value() != null)
                    .map(point -> new RuleEngine.Reading(point.measuredAt(), point.value()))
                    .toList();
            RuleEngine.LightStats stats = RuleEngine.lightMinutes(readings, from, now, LIGHT_ON_LUX, LIGHT_MAX_GAP);
            if (stats.coveredMinutes() >= LIGHT_MIN_COVERAGE_MINUTES) {
                // нормируем на сутки: часть интервала могла выпасть из-за разрывов связи
                lightHours.add(stats.lightMinutes() * 24.0 / stats.coveredMinutes());
            }
        }
        if (lightHours.isEmpty()) {
            return;
        }
        double actual = lightHours.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        Norm n = norm.get();
        double target = n.getTargetValue() != null ? n.getTargetValue() : (n.getMinValue() != null ? n.getMinValue() : n.getMaxValue());
        int actualLight = (int) Math.round(actual * 60);
        int deviation = (int) Math.round(Math.abs(actual - target) * 60);
        boolean below = n.getMinValue() != null && actual < n.getMinValue();
        boolean above = n.getMaxValue() != null && actual > n.getMaxValue();
        double outBy = below ? n.getMinValue() - actual : above ? actual - n.getMaxValue() : 0;
        RuleEngine.Level level = outBy == 0 ? RuleEngine.Level.NORMAL : outBy > 2 ? RuleEngine.Level.CRITICAL : RuleEngine.Level.WARNING;
        String status = switch (level) {
            case CRITICAL -> "CRITICAL";
            case WARNING -> "VIOLATION";
            default -> deviation > 15 ? "WARNING" : "NORMAL";
        };
        double compliance = Math.max(0, 100.0 - deviation * 100.0 / (target * 60));
        try {
            influx.saveLightingScheduleCompliance(context.house().getCode(), (int) Math.round(target * 60), actualLight,
                    (int) Math.round((24 - target) * 60), 24 * 60 - actualLight, deviation, compliance, status);
        } catch (Exception exception) {
            log.warn("Не удалось сохранить соблюдение программы освещения: {}", exception.getMessage());
        }
        String key = LIGHTING_PROGRAM + ":" + houseId + ":" + flocks.today(houseId);
        if (level == RuleEngine.Level.NORMAL) {
            automation.clear(LIGHTING_PROGRAM + ":" + houseId + ":" + flocks.today(houseId), "Программа освещения соблюдается")
                    .ifPresent(incident -> counter.cleared++);
            return;
        }
        String description = "Свет %s ч за сутки, темнота %s ч при норме света %s ч (день %d, %s)".formatted(
                format(actual), format(24 - actual), range(n), context.ageDay(), context.flock().getBreedCode());
        automation.raise(new Alert(key, rule.getCode(), IncidentType.LIGHTING_SCHEDULE_DEVIATION, level,
                "%s: нарушена программа освещения".formatted(context.house().getName()), description,
                houseId, context.house().getName(), null, null, context.flock().getId(), null));
        counter.raised++;
    }

    private Context context(UUID houseId) {
        House house = houses.findById(houseId).orElse(null);
        if (house == null || !house.isActive()) {
            return new Context(null, null, 0);
        }
        Flock flock = flocks.activeFlockOfHouse(houseId).orElse(null);
        if (flock == null) {
            return new Context(house, null, 0);
        }
        LocalDate today = flocks.today(houseId);
        int age = FlockService.ageOn(flock, today);
        return age < 0 ? new Context(house, null, 0) : new Context(house, flock, age);
    }

    private Alert alert(Rule rule, String key, IncidentType type, RuleEngine.Level level, Sensor sensor, Context context, String description) {
        Zone zone = sensor.getZoneId() == null ? null : zones.findById(sensor.getZoneId()).orElse(null);
        String where = context.house().getName() + (zone == null ? "" : ", " + zone.getName());
        return new Alert(key, rule.getCode(), type, level, "%s: %s".formatted(where, rule.getName().toLowerCase(Locale.ROOT)), description,
                context.house().getId(), context.house().getName(), sensor.getZoneId(), zone == null ? null : zone.getName(),
                context.flock() == null ? null : context.flock().getId(), sensor.getId());
    }

    /** Освещённость выше нормы — отдельный тип инцидента (LIGHTING_ILLUMINANCE_HIGH). */
    private static IncidentType typeFor(Rule rule, Norm norm, double value) {
        if (rule.getIncidentType() == IncidentType.LIGHTING_ILLUMINANCE_LOW && norm.getMaxValue() != null && value > norm.getMaxValue()) {
            return IncidentType.LIGHTING_ILLUMINANCE_HIGH;
        }
        return rule.getIncidentType();
    }

    static String describe(Rule rule, Norm norm, double value, Context context, RuleEngine.Level level) {
        int minutes = level == RuleEngine.Level.CRITICAL ? rule.getCriticalMinutes() : rule.getWarnMinutes();
        String breed = context.flock() == null ? "" : ", " + context.flock().getBreedCode();
        String unit = "C".equals(norm.getUnit()) ? "°C" : norm.getUnit();
        return "%s %s %s при норме %s %s (день %d%s) дольше %d мин".formatted(
                rule.getMetric().getDisplayName(), format(value), unit, range(norm), unit, context.ageDay(), breed, minutes);
    }

    static String range(Norm norm) {
        if (norm.getMinValue() != null && norm.getMaxValue() != null) {
            return format(norm.getMinValue()) + "–" + format(norm.getMaxValue());
        }
        if (norm.getMaxValue() != null) {
            return "не выше " + format(norm.getMaxValue());
        }
        return "не ниже " + format(norm.getMinValue());
    }

    static String format(double value) {
        return String.format(Locale.forLanguageTag("ru"), value == Math.rint(value) ? "%.0f" : "%.1f", value);
    }

    private static String formatMinutes(long minutes) {
        return minutes < 120 ? minutes + " мин" : "%d ч %d мин".formatted(minutes / 60, minutes % 60);
    }

    record Context(House house, Flock flock, int ageDay) {
    }

    private static final class Counter {
        int sensors;
        int raised;
        int cleared;
        int skipped;
    }
}
