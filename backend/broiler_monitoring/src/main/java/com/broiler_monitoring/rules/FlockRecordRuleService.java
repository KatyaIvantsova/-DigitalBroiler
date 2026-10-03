package com.broiler_monitoring.rules;

import com.broiler_monitoring.entity.DailyRecord;
import com.broiler_monitoring.entity.Flock;
import com.broiler_monitoring.entity.House;
import com.broiler_monitoring.entity.Norm;
import com.broiler_monitoring.entity.Rule;
import com.broiler_monitoring.enumerated.NormMetric;
import com.broiler_monitoring.repository.DailyRecordRepository;
import com.broiler_monitoring.repository.HouseRepository;
import com.broiler_monitoring.repository.RuleRepository;
import com.broiler_monitoring.service.FlockService;
import com.broiler_monitoring.service.NormService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Правила по ежедневному учёту (S4-05, docs/sprint2/08-rules-engine-spec.md п. 6): падёж выше нормы дня,
 * падение расхода корма и воды к прошлым суткам. Проверяются при каждом вводе, правке и удалении учёта.
 * Ключ дедупликации — правило + партия + дата; исправленный учёт закрывает инцидент так же, как возврат
 * показателя датчика в норму.
 */
@Service
public class FlockRecordRuleService {

    public static final String MORTALITY = "FLOCK_MORTALITY_DAILY";
    public static final String FEED_DROP = "FEED_DROP";
    public static final String WATER_DROP = "WATER_DROP";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Locale RU = Locale.forLanguageTag("ru");

    private final DailyRecordRepository dailyRecords;
    private final RuleRepository rules;
    private final NormService norms;
    private final HouseRepository houses;
    private final IncidentAutomationService automation;

    public FlockRecordRuleService(DailyRecordRepository dailyRecords, RuleRepository rules, NormService norms,
                                  HouseRepository houses, IncidentAutomationService automation) {
        this.dailyRecords = dailyRecords;
        this.rules = rules;
        this.norms = norms;
        this.houses = houses;
        this.automation = automation;
    }

    /** Учёт за дату изменился: проверить эту дату и следующую (падение считается к прошлым суткам). */
    @Transactional
    public void recordChanged(Flock flock, LocalDate date) {
        List<DailyRecord> records = dailyRecords.findByFlockIdOrderByRecordDateAsc(flock.getId());
        evaluate(flock, date, records);
        evaluate(flock, date.plusDays(1), records);
    }

    void evaluate(Flock flock, LocalDate date, List<DailyRecord> records) {
        Optional<DailyRecord> current = records.stream().filter(r -> r.getRecordDate().equals(date)).findFirst();
        Optional<DailyRecord> previous = records.stream().filter(r -> r.getRecordDate().equals(date.minusDays(1))).findFirst();
        int lossesBefore = records.stream()
                .filter(r -> r.getRecordDate().isBefore(date))
                .mapToInt(r -> r.getMortalityHeads() + r.getCulledHeads())
                .sum();
        int headsAtStart = flock.getPlacedHeads() - lossesBefore;
        int age = FlockService.ageOn(flock, date);

        rules.findById(MORTALITY).filter(Rule::isEnabled).ifPresent(rule -> {
            RuleEngine.Level level = RuleEngine.Level.NORMAL;
            String description = null;
            Optional<Norm> norm = norms.find(NormMetric.MORTALITY, flock.getBreedCode(), age);
            if (current.isPresent() && headsAtStart > 0 && norm.isPresent() && norm.get().getMaxValue() != null) {
                int losses = current.get().getMortalityHeads() + current.get().getCulledHeads();
                double pct = losses * 100.0 / headsAtStart;
                double max = norm.get().getMaxValue();
                level = level(pct / max, rule);
                description = "Падёж и выбраковка за %s (день %d): %d гол, %s %% при норме до %s %%".formatted(
                        date.format(DAY), age, losses, format(pct, 2), format(max, 2));
            }
            apply(rule, flock, date, level, description, current.isPresent());
        });
        check(rule(FEED_DROP), flock, date, age, current, previous, DailyRecord::getFeedConsumedKg, "Расход корма", "кг");
        check(rule(WATER_DROP), flock, date, age, current, previous, DailyRecord::getWaterConsumedL, "Расход воды", "л");
    }

    private Optional<Rule> rule(String code) {
        return rules.findById(code).filter(Rule::isEnabled);
    }

    private void check(Optional<Rule> found, Flock flock, LocalDate date, int age, Optional<DailyRecord> current,
                       Optional<DailyRecord> previous, Function<DailyRecord, Double> value, String what, String unit) {
        if (found.isEmpty()) {
            return;
        }
        Rule rule = found.get();
        Double now = current.map(value).orElse(null);
        Double before = previous.map(value).orElse(null);
        RuleEngine.Level level = RuleEngine.Level.NORMAL;
        String description = null;
        if (now != null && before != null && before > 0) {
            double drop = (before - now) * 100.0 / before;
            double warn = rule.getWarnDelta() == null ? 10.0 : rule.getWarnDelta();
            double critical = rule.getCriticalDelta() == null ? 2 * warn : rule.getCriticalDelta();
            level = drop > critical ? RuleEngine.Level.CRITICAL : drop > warn ? RuleEngine.Level.WARNING : RuleEngine.Level.NORMAL;
            description = "%s за %s (день %d): %s %s, на %s %% меньше, чем накануне (%s %s)".formatted(
                    what, date.format(DAY), age, format(now, 1), unit, format(drop, 1), format(before, 1), unit);
        }
        apply(rule, flock, date, level, description, current.isPresent());
    }

    /** Кратность нормы падежа → уровень: выше warn_delta — предупреждение, выше critical_delta — критично. */
    private static RuleEngine.Level level(double ratio, Rule rule) {
        double warn = rule.getWarnDelta() == null ? 1.0 : rule.getWarnDelta();
        double critical = rule.getCriticalDelta() == null ? 2 * warn : rule.getCriticalDelta();
        if (ratio > critical) {
            return RuleEngine.Level.CRITICAL;
        }
        return ratio > warn ? RuleEngine.Level.WARNING : RuleEngine.Level.NORMAL;
    }

    private void apply(Rule rule, Flock flock, LocalDate date, RuleEngine.Level level, String description, boolean recordExists) {
        String key = dedupKey(rule.getCode(), flock, date);
        if (level == RuleEngine.Level.WARNING || level == RuleEngine.Level.CRITICAL) {
            House house = houses.findById(flock.getHouseId()).orElse(null);
            String houseName = house == null ? null : house.getName();
            String title = "%s, партия %s: %s".formatted(houseName, flock.getCode(), rule.getName().toLowerCase(RU));
            automation.raise(new Alert(key, rule.getCode(), rule.getIncidentType(), level, title, description,
                    flock.getHouseId(), houseName, null, null, flock.getId(), null));
        } else {
            automation.clear(key, recordExists ? "Учёт исправлен, показатель в норме" : "Запись учёта удалена");
        }
    }

    public static String dedupKey(String ruleCode, Flock flock, LocalDate date) {
        return ruleCode + ":" + flock.getId() + ":" + date;
    }

    private static String format(double value, int digits) {
        return String.format(RU, "%." + digits + "f", value);
    }
}
