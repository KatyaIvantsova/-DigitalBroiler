package com.broiler_monitoring.kpi;

/**
 * Формулы KPI партии (docs/sprint1/04-kpi-formulas.md). Чистые функции без доступа к БД:
 * сервис KPI (S4-01) собирает входные данные из партии, учёта и взвешиваний и вызывает {@link #calculate}.
 * Эталонные партии для проверки — src/test/resources/kpi/reference-flocks.json (S3-08).
 */
public final class KpiFormulas {

    /** Средний вес суточного цыплёнка, если при посадке не взвешивали. */
    public static final double DEFAULT_PLACED_WEIGHT_G = 42.0;

    private KpiFormulas() {
    }

    /**
     * @param placedHeads     N₀ — посажено голов
     * @param placedAvgWeightG W₀ — средний вес при посадке, г (null — 42 г)
     * @param mortality       D — падёж за период
     * @param culled          C — выбраковка за период
     * @param shippedHeads    сдано голов (для закрытой партии), иначе null — тогда N = N₀ − D − C
     * @param avgWeightKg     W — средний живой вес, кг (последнее взвешивание или вес сдачи); null — нет данных
     * @param ageDays         T — возраст или длительность цикла, дн
     * @param feedKg          F — израсходовано корма, кг; null — нет данных
     */
    public record Input(int placedHeads, Double placedAvgWeightG, int mortality, int culled, Integer shippedHeads,
                        Double avgWeightKg, int ageDays, Double feedKg) {
    }

    /** null в поле — показатель не считается (нет данных), а не ноль. */
    public record Result(int heads, double survivalPct, double lossPct, Double liveMassKg, Double fcr, Double adgG,
                         Double epef, int cycleDays) {
    }

    public static Result calculate(Input input) {
        if (input.placedHeads() <= 0) {
            throw new IllegalArgumentException("Посажено голов должно быть больше нуля");
        }
        int heads = input.shippedHeads() != null ? input.shippedHeads() : input.placedHeads() - input.mortality() - input.culled();
        double survival = heads * 100.0 / input.placedHeads();
        double loss = (input.mortality() + input.culled()) * 100.0 / input.placedHeads();
        Double liveMass = input.avgWeightKg() == null ? null : heads * input.avgWeightKg();
        Double fcr = liveMass == null || liveMass == 0 || input.feedKg() == null ? null : input.feedKg() / liveMass;
        double w0 = input.placedAvgWeightG() == null ? DEFAULT_PLACED_WEIGHT_G : input.placedAvgWeightG();
        Double adg = input.avgWeightKg() == null || input.ageDays() <= 0 ? null : (input.avgWeightKg() * 1000 - w0) / input.ageDays();
        Double epef = fcr == null || fcr == 0 || input.ageDays() <= 0 ? null
                : survival * input.avgWeightKg() / (input.ageDays() * fcr) * 100;
        return new Result(heads, survival, loss, liveMass, fcr, adg, epef, input.ageDays());
    }

    /** Округление для интерфейса: проценты — 2 знака, FCR — 3, ADG — 1, EPEF — целое. */
    public static double round(double value, int digits) {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }
}
