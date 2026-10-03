package com.broiler_monitoring.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoublePredicate;

/**
 * Чистая логика движка правил (docs/sprint2/08-rules-engine-spec.md, п. 4).
 * Показание считается действующим до следующего показания (sample-and-hold), поэтому
 * «значение вне нормы 30 минут» = последнее показание до начала окна и все показания внутри окна вне нормы.
 */
public final class RuleEngine {

    /** Данные старше этого срока не меняют уровень: потерю связи ловит правило «нет данных». */
    public static final Duration STALE_AFTER = Duration.ofMinutes(30);

    public enum Level { NORMAL, WARNING, CRITICAL, UNCHANGED }

    public record Reading(Instant at, double value) {
    }

    public record Thresholds(Double min, Double max, Double criticalDelta, int warnMinutes, int criticalMinutes, int clearMinutes) {
    }

    private RuleEngine() {
    }

    public static Level evaluate(List<Reading> readings, Thresholds thresholds, Instant now) {
        List<Reading> sorted = readings.stream()
                .filter(reading -> !reading.at().isAfter(now))
                .sorted(Comparator.comparing(Reading::at))
                .toList();
        if (sorted.isEmpty() || sorted.getLast().at().isBefore(now.minus(STALE_AFTER))) {
            return Level.UNCHANGED;
        }
        Double min = thresholds.min();
        Double max = thresholds.max();
        DoublePredicate outside = value -> (min != null && value < min) || (max != null && value > max);
        if (thresholds.criticalDelta() != null) {
            double delta = thresholds.criticalDelta();
            DoublePredicate critical = value -> (min != null && value < min - delta) || (max != null && value > max + delta);
            if (holds(sorted, now, thresholds.criticalMinutes(), critical)) {
                return Level.CRITICAL;
            }
        }
        if (holds(sorted, now, thresholds.warnMinutes(), outside)) {
            return Level.WARNING;
        }
        if (holds(sorted, now, thresholds.clearMinutes(), outside.negate())) {
            return Level.NORMAL;
        }
        return Level.UNCHANGED;
    }

    /** Условие выполнялось всё окно: показание, действовавшее на начало окна, и все показания внутри окна. */
    static boolean holds(List<Reading> sorted, Instant now, int minutes, DoublePredicate condition) {
        Instant windowStart = now.minus(Duration.ofMinutes(minutes));
        Reading atStart = null;
        for (Reading reading : sorted) {
            if (reading.at().isAfter(windowStart)) {
                if (!condition.test(reading.value())) {
                    return false;
                }
            } else {
                atStart = reading;
            }
        }
        if (minutes == 0) {
            return condition.test(sorted.getLast().value());
        }
        return atStart != null && condition.test(atStart.value());
    }

    /**
     * Минуты со светом за период по показаниям датчика освещённости.
     * Разрывы в данных дольше {@code maxGap} не учитываются ни как свет, ни как темнота.
     */
    public static LightStats lightMinutes(List<Reading> readings, Instant from, Instant to, double onThresholdLux, Duration maxGap) {
        List<Reading> sorted = readings.stream()
                .sorted(Comparator.comparing(Reading::at))
                .toList();
        long light = 0;
        long covered = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Reading reading = sorted.get(i);
            Instant start = reading.at().isBefore(from) ? from : reading.at();
            Instant next = i + 1 < sorted.size() ? sorted.get(i + 1).at() : to;
            Instant end = next.isAfter(to) ? to : next;
            if (Duration.between(reading.at(), next).compareTo(maxGap) > 0) {
                end = reading.at().plus(maxGap).isAfter(to) ? to : reading.at().plus(maxGap);
            }
            if (!end.isAfter(start)) {
                continue;
            }
            long minutes = Duration.between(start, end).toMinutes();
            covered += minutes;
            if (reading.value() >= onThresholdLux) {
                light += minutes;
            }
        }
        return new LightStats(light, covered);
    }

    public record LightStats(long lightMinutes, long coveredMinutes) {
    }
}
