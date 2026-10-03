package com.broiler_monitoring.dto.kpi;

import java.time.LocalDate;
import java.util.UUID;

/**
 * KPI партии (S4-01, docs/sprint1/04-kpi-formulas.md). Поголовье и сохранность — на сегодня (для закрытой — на сдачу);
 * живая масса, FCR, привес и EPEF — на дату веса {@code weightDate}: для активной партии это последнее взвешивание,
 * корм и потери берутся по эту дату включительно. null — показатель не считается (нет данных), а не ноль.
 */
public record FlockKpiResponse(
        UUID flockId,
        boolean closed,
        Integer ageDays,
        int placedHeads,
        int mortality,
        int culled,
        int heads,
        /** Закрытая партия: посажено − падёж − выбраковка − сдано, если больше нуля. */
        Integer unaccountedHeads,
        double survivalPct,
        double lossPct,
        Double avgWeightG,
        LocalDate weightDate,
        Integer weightAgeDays,
        Double feedKg,
        /** Сколько суток до даты веса без данных по корму — FCR неполный. */
        int feedMissingDays,
        Double liveMassKg,
        Double fcr,
        Double adgG,
        Double epef,
        Double normWeightG,
        Double weightDeviationPct,
        Double normFcr,
        Double fcrDeviation
) {
}
