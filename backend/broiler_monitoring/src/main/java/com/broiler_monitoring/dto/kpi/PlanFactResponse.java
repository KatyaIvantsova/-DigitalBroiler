package com.broiler_monitoring.dto.kpi;

import java.time.LocalDate;
import java.util.List;

/** План-факт по кривой кросса (S4-02): факт по каждому взвешиванию и норма по дням для графика. */
public record PlanFactResponse(String breedCode, List<Point> weighings, List<NormPoint> curve) {

    /** Взвешивание против нормы на его возраст. FCR — нарастающим итогом на дату взвешивания. */
    public record Point(
            LocalDate date,
            int ageDay,
            double weightG,
            Double uniformityPct,
            Double normWeightG,
            Double normWeightMinG,
            Double weightDeviationG,
            Double weightDeviationPct,
            Double fcr,
            Double normFcr,
            Double fcrDeviation
    ) {
    }

    /** Норма на день: целевой вес и FCR (null — в справочнике нет). */
    public record NormPoint(int ageDay, Double weightG, Double fcr) {
    }
}
