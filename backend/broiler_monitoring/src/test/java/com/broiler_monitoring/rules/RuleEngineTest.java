package com.broiler_monitoring.rules;

import com.broiler_monitoring.rules.RuleEngine.Level;
import com.broiler_monitoring.rules.RuleEngine.Reading;
import com.broiler_monitoring.rules.RuleEngine.Thresholds;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Уровни движка правил по спецификации docs/sprint2/08-rules-engine-spec.md, п. 4. */
class RuleEngineTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    /** Температура: норма 26–28 °C, предупреждение через 30 мин, критично ±1 °C 15 мин, закрытие 15 мин. */
    private static final Thresholds TEMPERATURE = new Thresholds(26.0, 28.0, 1.0, 30, 15, 15);

    /** Показания раз в 5 минут за последние {@code minutes} минут, от старых к новым. */
    private static List<Reading> series(int minutes, double... values) {
        List<Reading> readings = new ArrayList<>();
        int count = minutes / 5 + 1;
        for (int i = 0; i < count; i++) {
            double value = values[Math.min(i, values.length - 1)];
            readings.add(new Reading(NOW.minus(Duration.ofMinutes(minutes - i * 5L)), value));
        }
        return readings;
    }

    @Test
    void warningWhenOutsideNormLongerThanWarnWindow() {
        assertThat(RuleEngine.evaluate(series(40, 28.5), TEMPERATURE, NOW)).isEqualTo(Level.WARNING);
    }

    @Test
    void shortDeviationDoesNotRaise() {
        // 20 минут нормы, затем 20 минут выше нормы — меньше окна предупреждения в 30 минут
        assertThat(RuleEngine.evaluate(series(40, 27, 27, 27, 27, 28.5), TEMPERATURE, NOW)).isEqualTo(Level.UNCHANGED);
    }

    @Test
    void criticalWhenBeyondDeltaForCriticalWindow() {
        assertThat(RuleEngine.evaluate(series(20, 29.5), TEMPERATURE, NOW)).isEqualTo(Level.CRITICAL);
    }

    @Test
    void normalAfterClearWindowInsideNorm() {
        assertThat(RuleEngine.evaluate(series(40, 28.5, 28.5, 28.5, 28.5, 27), TEMPERATURE, NOW)).isEqualTo(Level.NORMAL);
    }

    @Test
    void maxOnlyNormChecksUpperBound() {
        Thresholds co2 = new Thresholds(null, 2500.0, 500.0, 30, 15, 15);
        assertThat(RuleEngine.evaluate(series(40, 400), co2, NOW)).isEqualTo(Level.NORMAL);
        assertThat(RuleEngine.evaluate(series(40, 2700), co2, NOW)).isEqualTo(Level.WARNING);
        assertThat(RuleEngine.evaluate(series(40, 3100), co2, NOW)).isEqualTo(Level.CRITICAL);
    }

    @Test
    void staleDataDoesNotChangeLevel() {
        List<Reading> old = List.of(
                new Reading(NOW.minus(Duration.ofMinutes(120)), 35),
                new Reading(NOW.minus(Duration.ofMinutes(90)), 35));
        assertThat(RuleEngine.evaluate(old, TEMPERATURE, NOW)).isEqualTo(Level.UNCHANGED);
        assertThat(RuleEngine.evaluate(List.of(), TEMPERATURE, NOW)).isEqualTo(Level.UNCHANGED);
    }

    @Test
    void sparseReadingsHoldValueUntilNext() {
        // Датчик шлёт раз в 10 минут: показание 35 минут назад действует до следующего
        List<Reading> readings = List.of(
                new Reading(NOW.minus(Duration.ofMinutes(35)), 28.6),
                new Reading(NOW.minus(Duration.ofMinutes(25)), 28.7),
                new Reading(NOW.minus(Duration.ofMinutes(15)), 28.6),
                new Reading(NOW.minus(Duration.ofMinutes(5)), 28.8));
        assertThat(RuleEngine.evaluate(readings, TEMPERATURE, NOW)).isEqualTo(Level.WARNING);
    }

    @Test
    void lightMinutesCountsOnlyLightAndSkipsGaps() {
        Instant from = NOW.minus(Duration.ofHours(24));
        List<Reading> readings = new ArrayList<>();
        // 18 часов света (20 лк), 6 часов темноты (0 лк), показания раз в 10 минут
        for (int minute = 0; minute < 24 * 60; minute += 10) {
            readings.add(new Reading(from.plus(Duration.ofMinutes(minute)), minute < 18 * 60 ? 20 : 0));
        }
        RuleEngine.LightStats stats = RuleEngine.lightMinutes(readings, from, NOW, 1.0, Duration.ofMinutes(60));
        assertThat(stats.lightMinutes()).isEqualTo(18 * 60);
        assertThat(stats.coveredMinutes()).isEqualTo(24 * 60);

        // Разрыв связи на 4 часа не считается ни светом, ни темнотой
        List<Reading> withGap = readings.stream()
                .filter(reading -> reading.at().isBefore(from.plus(Duration.ofHours(4))) || !reading.at().isBefore(from.plus(Duration.ofHours(8))))
                .toList();
        RuleEngine.LightStats gapStats = RuleEngine.lightMinutes(withGap, from, NOW, 1.0, Duration.ofMinutes(60));
        assertThat(gapStats.coveredMinutes()).isEqualTo(24 * 60 - 4 * 60 + 60 - 10);
    }
}
