package com.broiler_monitoring.kpi;

import com.broiler_monitoring.dto.kpi.FlockKpiResponse;
import com.broiler_monitoring.dto.kpi.PlanFactResponse;
import com.broiler_monitoring.entity.DailyRecord;
import com.broiler_monitoring.entity.Flock;
import com.broiler_monitoring.entity.Norm;
import com.broiler_monitoring.entity.Weighing;
import com.broiler_monitoring.enumerated.FlockStatus;
import com.broiler_monitoring.enumerated.NormMetric;
import com.broiler_monitoring.repository.DailyRecordRepository;
import com.broiler_monitoring.repository.WeighingRepository;
import com.broiler_monitoring.service.FlockService;
import com.broiler_monitoring.service.NormService;
import com.broiler_monitoring.service.StructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * KPI партии и план-факт по кривой кросса (S4-01, S4-02). Формулы — {@link KpiFormulas}, данные — партия,
 * ежедневный учёт и взвешивания; правка учёта задним числом пересчитывает всё при следующем запросе.
 */
@Service
public class KpiService {

    /** Длина кривой нормы, если у партии не задан плановый возраст. */
    private static final int DEFAULT_CURVE_DAYS = 42;

    private final FlockService flocks;
    private final DailyRecordRepository dailyRecords;
    private final WeighingRepository weighings;
    private final NormService norms;
    private final StructureService structure;

    public KpiService(FlockService flocks, DailyRecordRepository dailyRecords, WeighingRepository weighings,
                      NormService norms, StructureService structure) {
        this.flocks = flocks;
        this.dailyRecords = dailyRecords;
        this.weighings = weighings;
        this.norms = norms;
        this.structure = structure;
    }

    @Transactional(readOnly = true)
    public FlockKpiResponse kpi(UUID flockId) {
        Flock flock = flocks.getVisible(flockId);
        List<DailyRecord> records = dailyRecords.findByFlockIdOrderByRecordDateAsc(flockId);
        int mortality = records.stream().mapToInt(DailyRecord::getMortalityHeads).sum();
        int culled = records.stream().mapToInt(DailyRecord::getCulledHeads).sum();
        boolean closed = flock.getStatus() == FlockStatus.CLOSED && flock.getClosedAt() != null;
        Integer ageDays = flocks.ageDays(flock);

        // Сегодняшнее поголовье и сохранность (для закрытой — по сдаче)
        KpiFormulas.Result current = KpiFormulas.calculate(new KpiFormulas.Input(flock.getPlacedHeads(), flock.getPlacedAvgWeightG(),
                mortality, culled, closed ? flock.getShippedHeads() : null, null, ageDays == null ? 0 : Math.max(ageDays, 0), null));
        Integer unaccounted = null;
        if (closed && flock.getShippedHeads() != null) {
            int diff = flock.getPlacedHeads() - mortality - culled - flock.getShippedHeads();
            unaccounted = diff > 0 ? diff : null;
        }

        // Продуктивность — на дату веса: сдача для закрытой партии, иначе последнее взвешивание
        LocalDate weightDate = null;
        Double weightKg = null;
        Integer shipped = null;
        if (closed && flock.getShippedHeads() != null && flock.getShippedHeads() > 0 && flock.getShippedLiveWeightKg() != null) {
            weightDate = flock.getClosedAt();
            weightKg = flock.getShippedLiveWeightKg() / flock.getShippedHeads();
            shipped = flock.getShippedHeads();
        } else {
            Optional<Weighing> last = lastWeighing(flockId);
            if (last.isPresent()) {
                weightDate = dateOf(flock, last.get());
                weightKg = last.get().getAvgWeightG() / 1000.0;
            }
        }

        KpiFormulas.Result performance = null;
        Double feedKg = null;
        int feedMissing = 0;
        Integer weightAge = null;
        Double normWeight = null;
        Double normFcr = null;
        if (weightDate != null) {
            LocalDate until = weightDate;
            weightAge = FlockService.ageOn(flock, weightDate);
            List<DailyRecord> period = records.stream().filter(r -> !r.getRecordDate().isAfter(until)).toList();
            feedKg = feedUntil(period);
            feedMissing = missingFeedDays(flock, period, until);
            int periodMortality = period.stream().mapToInt(DailyRecord::getMortalityHeads).sum();
            int periodCulled = period.stream().mapToInt(DailyRecord::getCulledHeads).sum();
            performance = KpiFormulas.calculate(new KpiFormulas.Input(flock.getPlacedHeads(), flock.getPlacedAvgWeightG(),
                    periodMortality, periodCulled, shipped, weightKg, Math.max(weightAge, 0), feedKg));
            normWeight = target(norms.find(NormMetric.BODY_WEIGHT, flock.getBreedCode(), weightAge));
            normFcr = target(norms.find(NormMetric.FCR, flock.getBreedCode(), weightAge));
        }

        Double weightG = weightKg == null ? null : weightKg * 1000;
        Double fcr = performance == null ? null : performance.fcr();
        return new FlockKpiResponse(
                flock.getId(), closed, ageDays, flock.getPlacedHeads(), mortality, culled, current.heads(), unaccounted,
                KpiFormulas.round(current.survivalPct(), 2), KpiFormulas.round(current.lossPct(), 2),
                round(weightG, 0), weightDate, weightAge, round(feedKg, 1), feedMissing,
                performance == null ? null : round(performance.liveMassKg(), 1),
                round(fcr, 3),
                performance == null ? null : round(performance.adgG(), 1),
                performance == null || performance.epef() == null ? null : (double) Math.round(performance.epef()),
                normWeight,
                weightG == null || normWeight == null || normWeight == 0 ? null : round((weightG - normWeight) * 100 / normWeight, 2),
                normFcr,
                fcr == null || normFcr == null ? null : round(fcr - normFcr, 3));
    }

    @Transactional(readOnly = true)
    public PlanFactResponse planFact(UUID flockId) {
        Flock flock = flocks.getVisible(flockId);
        List<DailyRecord> records = dailyRecords.findByFlockIdOrderByRecordDateAsc(flockId);
        IntFunction<Optional<Norm>> weightNorm = norms.lookup(NormMetric.BODY_WEIGHT, flock.getBreedCode());
        IntFunction<Optional<Norm>> fcrNorm = norms.lookup(NormMetric.FCR, flock.getBreedCode());

        List<PlanFactResponse.Point> points = new ArrayList<>();
        for (Weighing weighing : weighings.findByFlockIdOrderByWeighedAtAsc(flockId)) {
            LocalDate date = dateOf(flock, weighing);
            int age = FlockService.ageOn(flock, date);
            List<DailyRecord> period = records.stream().filter(r -> !r.getRecordDate().isAfter(date)).toList();
            KpiFormulas.Result result = KpiFormulas.calculate(new KpiFormulas.Input(flock.getPlacedHeads(), flock.getPlacedAvgWeightG(),
                    period.stream().mapToInt(DailyRecord::getMortalityHeads).sum(),
                    period.stream().mapToInt(DailyRecord::getCulledHeads).sum(),
                    null, weighing.getAvgWeightG() / 1000.0, Math.max(age, 0), feedUntil(period)));
            Optional<Norm> weight = weightNorm.apply(age);
            Double target = target(weight);
            Double normFcr = target(fcrNorm.apply(age));
            Double fcr = round(result.fcr(), 3);
            points.add(new PlanFactResponse.Point(date, age, weighing.getAvgWeightG(), weighing.getUniformityPct(),
                    target, weight.map(Norm::getMinValue).orElse(null),
                    target == null ? null : round(weighing.getAvgWeightG() - target, 0),
                    target == null || target == 0 ? null : round((weighing.getAvgWeightG() - target) * 100 / target, 2),
                    fcr, normFcr, fcr == null || normFcr == null ? null : round(fcr - normFcr, 3)));
        }

        Integer age = flocks.ageDays(flock);
        int lastDay = Math.max(flock.getTargetAgeDays() != null ? flock.getTargetAgeDays() : DEFAULT_CURVE_DAYS, age == null ? 0 : age);
        List<PlanFactResponse.NormPoint> curve = new ArrayList<>();
        for (int day = 0; day <= lastDay; day++) {
            Double weight = target(weightNorm.apply(day));
            Double fcr = target(fcrNorm.apply(day));
            if (weight != null || fcr != null) {
                curve.add(new PlanFactResponse.NormPoint(day, weight, fcr));
            }
        }
        return new PlanFactResponse(flock.getBreedCode(), points, curve);
    }

    private Optional<Weighing> lastWeighing(UUID flockId) {
        List<Weighing> all = weighings.findByFlockIdOrderByWeighedAtAsc(flockId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
    }

    private LocalDate dateOf(Flock flock, Weighing weighing) {
        ZoneId zone = structure.timezoneOfHouse(flock.getHouseId());
        return weighing.getWeighedAt().atZone(zone).toLocalDate();
    }

    /** Корм по дату включительно; null — по корму нет ни одной записи. */
    private static Double feedUntil(List<DailyRecord> period) {
        List<Double> values = period.stream().map(DailyRecord::getFeedConsumedKg).filter(v -> v != null).toList();
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Сутки с посадки по дату без расхода корма (день посадки не считается — птицу ещё не кормили). */
    private static int missingFeedDays(Flock flock, List<DailyRecord> period, LocalDate until) {
        long withFeed = period.stream()
                .filter(r -> r.getFeedConsumedKg() != null && r.getRecordDate().isAfter(flock.getPlacedAt()))
                .count();
        long days = Math.max(0, FlockService.ageOn(flock, until));
        return (int) Math.max(0, days - withFeed);
    }

    private static Double target(Optional<Norm> norm) {
        return norm.map(Norm::getTargetValue).orElse(null);
    }

    private static Double round(Double value, int digits) {
        return value == null ? null : KpiFormulas.round(value, digits);
    }
}
