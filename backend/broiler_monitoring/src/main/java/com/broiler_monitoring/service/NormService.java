package com.broiler_monitoring.service;

import com.broiler_monitoring.dto.norms.NormImportResult;
import com.broiler_monitoring.dto.norms.NormUpdateRequest;
import com.broiler_monitoring.entity.Norm;
import com.broiler_monitoring.enumerated.NormMetric;
import com.broiler_monitoring.repository.BreedRepository;
import com.broiler_monitoring.repository.NormRepository;
import com.broiler_monitoring.security.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Справочник норм (S3-01, S3-02): поиск нормы для возраста и кросса, правка с версионированием, импорт CSV.
 * При пустом справочнике на старте загружает classpath:norms/norms-v1.csv (нормы S1-03 и S2-11).
 */
@Service
public class NormService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NormService.class);
    static final String CSV_HEADER = "breed;metric;age_from;age_to;min;target;max;unit;source";

    private final NormRepository norms;
    private final BreedRepository breeds;
    private final AuditService audit;
    private final Clock clock;

    public NormService(NormRepository norms, BreedRepository breeds, AuditService audit, Clock clock) {
        this.norms = norms;
        this.breeds = breeds;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (norms.existsByValidToIsNull()) {
            return;
        }
        try (InputStream input = new ClassPathResource("norms/norms-v1.csv").getInputStream()) {
            NormImportResult result = importCsv(new String(input.readAllBytes(), StandardCharsets.UTF_8), "Начальная загрузка norms-v1.csv");
            log.info("Справочник норм загружен: {} строк, ошибок {}", result.created(), result.errors().size());
        }
    }

    public List<Norm> findCurrent(NormMetric metric, String breedCode) {
        return norms.findByValidToIsNullOrderByMetricAscBreedCodeAscAgeFromDayAsc().stream()
                .filter(norm -> metric == null || norm.getMetric() == metric)
                .filter(norm -> breedCode == null || breedCode.isBlank() || Objects.equals(norm.getBreedCode(), breedCode) || norm.getBreedCode() == null)
                .toList();
    }

    /** Действующая норма для показателя, кросса и возраста. Норма конкретного кросса важнее общей. */
    public Optional<Norm> find(NormMetric metric, String breedCode, int ageDay) {
        return lookup(metric, breedCode).apply(ageDay);
    }

    /** Поиск нормы по возрасту для одного показателя и кросса: справочник читается один раз (кривые для графиков). */
    public IntFunction<Optional<Norm>> lookup(NormMetric metric, String breedCode) {
        List<Norm> candidates = norms.findByMetricAndValidToIsNull(metric).stream()
                .filter(norm -> norm.getBreedCode() == null || norm.getBreedCode().equals(breedCode))
                .toList();
        return ageDay -> candidates.stream()
                .filter(norm -> norm.covers(ageDay))
                .min(Comparator.comparing((Norm norm) -> norm.getBreedCode() == null ? 1 : 0)
                        .thenComparing(norm -> norm.getAgeToDay() - norm.getAgeFromDay()));
    }

    /** Все версии нормы, начиная с текущей. */
    public List<Norm> history(UUID id) {
        Norm norm = get(id);
        List<Norm> chain = new ArrayList<>();
        Norm cursor = norm;
        while (true) {
            Optional<Norm> next = norms.findByPreviousId(cursor.getId());
            if (next.isEmpty()) break;
            cursor = next.get();
        }
        while (cursor != null) {
            chain.add(cursor);
            cursor = cursor.getPreviousId() == null ? null : norms.findById(cursor.getPreviousId()).orElse(null);
        }
        return chain;
    }

    @Transactional
    public Norm update(UUID id, NormUpdateRequest request) {
        Norm current = get(id);
        if (current.getValidTo() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это старая версия нормы — откройте действующую");
        }
        validateBounds(request.minValue(), request.targetValue(), request.maxValue());
        Norm next = newVersion(current, request.minValue(), request.targetValue(), request.maxValue(),
                request.source() == null || request.source().isBlank() ? current.getSource() : request.source().trim(),
                request.comment().trim());
        audit.record(AuditService.NORM, current.getId(), "UPDATED", describe(next) + ": версия " + next.getVersion(),
                AuditService.changes()
                        .field("Минимум", current.getMinValue(), next.getMinValue())
                        .field("Цель", current.getTargetValue(), next.getTargetValue())
                        .field("Максимум", current.getMaxValue(), next.getMaxValue())
                        .field("Источник", current.getSource(), next.getSource())
                        .value("Причина", next.getChangeComment()));
        // Запись журнала по новой версии — чтобы история находилась по любому id из цепочки
        audit.record(AuditService.NORM, next.getId(), "UPDATED", describe(next) + ": версия " + next.getVersion(),
                AuditService.changes().value("Причина", next.getChangeComment()));
        return next;
    }

    /**
     * Импорт таблицы норм (CSV, разделитель «;», заголовок как в norms-v1.csv).
     * Строка с тем же показателем, кроссом и интервалом возраста заменяет действующую норму новой версией.
     */
    @Transactional
    public NormImportResult importCsv(String csv, String comment) {
        List<String> errors = new ArrayList<>();
        int created = 0, updated = 0, unchanged = 0;
        String[] lines = csv.replace("﻿", "").split("\\R");
        if (lines.length == 0 || !lines[0].trim().equalsIgnoreCase(CSV_HEADER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Первая строка файла должна быть: " + CSV_HEADER);
        }
        List<Norm> current = new ArrayList<>(norms.findByValidToIsNullOrderByMetricAscBreedCodeAscAgeFromDayAsc());
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            try {
                Norm parsed = parseLine(line);
                Optional<Norm> existing = current.stream()
                        .filter(norm -> norm.getMetric() == parsed.getMetric()
                                && Objects.equals(norm.getBreedCode(), parsed.getBreedCode())
                                && norm.getAgeFromDay() == parsed.getAgeFromDay()
                                && norm.getAgeToDay() == parsed.getAgeToDay())
                        .findFirst();
                if (existing.isEmpty()) {
                    parsed.setChangedBy(CurrentActor.get().name());
                    parsed.setChangeComment(comment);
                    parsed.setValidFrom(clock.instant());
                    norms.save(parsed);
                    current.add(parsed);
                    created++;
                } else if (sameValues(existing.get(), parsed)) {
                    unchanged++;
                } else {
                    Norm next = newVersion(existing.get(), parsed.getMinValue(), parsed.getTargetValue(), parsed.getMaxValue(), parsed.getSource(), comment);
                    current.remove(existing.get());
                    current.add(next);
                    updated++;
                }
            } catch (IllegalArgumentException exception) {
                errors.add("Строка %d: %s".formatted(i + 1, exception.getMessage()));
            }
        }
        if (!errors.isEmpty() && created + updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }
        if (created + updated > 0) {
            audit.record(AuditService.NORM, "import", "IMPORTED",
                    "Импорт норм: новых %d, изменено %d".formatted(created, updated),
                    AuditService.changes().value("Комментарий", comment).value("Ошибок", errors.isEmpty() ? null : errors.size()));
        }
        return new NormImportResult(created, updated, unchanged, errors);
    }

    public String exportCsv() {
        StringBuilder builder = new StringBuilder(CSV_HEADER).append('\n');
        for (Norm norm : findCurrent(null, null)) {
            builder.append(String.join(";",
                    norm.getBreedCode() == null ? "" : norm.getBreedCode(),
                    norm.getMetric().name(),
                    String.valueOf(norm.getAgeFromDay()),
                    String.valueOf(norm.getAgeToDay()),
                    format(norm.getMinValue()), format(norm.getTargetValue()), format(norm.getMaxValue()),
                    norm.getUnit(),
                    norm.getSource().replace(';', ',').replace('\n', ' '))).append('\n');
        }
        return builder.toString();
    }

    public Norm get(UUID id) {
        return norms.findById(id).orElseThrow(() -> StructureService.notFound("Норма", id));
    }

    public static String describe(Norm norm) {
        return "%s, %s, дни %d–%d".formatted(norm.getMetric().getDisplayName(),
                norm.getBreedCode() == null ? "все кроссы" : norm.getBreedCode(), norm.getAgeFromDay(), norm.getAgeToDay());
    }

    private Norm newVersion(Norm current, Double min, Double target, Double max, String source, String comment) {
        current.setValidTo(clock.instant());
        norms.save(current);
        Norm next = new Norm();
        next.setBreedCode(current.getBreedCode());
        next.setMetric(current.getMetric());
        next.setAgeFromDay(current.getAgeFromDay());
        next.setAgeToDay(current.getAgeToDay());
        next.setUnit(current.getUnit());
        next.setMinValue(min);
        next.setTargetValue(target);
        next.setMaxValue(max);
        next.setSource(source);
        next.setVersion(current.getVersion() + 1);
        next.setPreviousId(current.getId());
        next.setValidFrom(clock.instant());
        next.setChangedBy(CurrentActor.get().name());
        next.setChangeComment(comment);
        return norms.save(next);
    }

    private Norm parseLine(String line) {
        String[] cells = line.split(";", -1);
        if (cells.length < 9) {
            throw new IllegalArgumentException("нужно 9 колонок, а не " + cells.length);
        }
        Norm norm = new Norm();
        String breed = cells[0].trim();
        if (!breed.isEmpty() && !breeds.existsById(breed)) {
            throw new IllegalArgumentException("неизвестный кросс " + breed);
        }
        norm.setBreedCode(breed.isEmpty() ? null : breed);
        try {
            norm.setMetric(NormMetric.valueOf(cells[1].trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("неизвестный показатель " + cells[1].trim());
        }
        norm.setAgeFromDay(parseInt(cells[2], "age_from"));
        norm.setAgeToDay(parseInt(cells[3], "age_to"));
        if (norm.getAgeFromDay() < 0 || norm.getAgeToDay() < norm.getAgeFromDay()) {
            throw new IllegalArgumentException("неверный интервал возраста");
        }
        norm.setMinValue(parseDouble(cells[4]));
        norm.setTargetValue(parseDouble(cells[5]));
        norm.setMaxValue(parseDouble(cells[6]));
        validateBounds(norm.getMinValue(), norm.getTargetValue(), norm.getMaxValue());
        norm.setUnit(required(cells[7], "unit"));
        norm.setSource(required(cells[8], "source"));
        return norm;
    }

    private static void validateBounds(Double min, Double target, Double max) {
        if (min == null && target == null && max == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нужна хотя бы одна граница нормы");
        }
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Минимум больше максимума");
        }
        if (target != null && ((min != null && target < min) || (max != null && target > max))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цель вне границ нормы");
        }
    }

    private static boolean sameValues(Norm a, Norm b) {
        return Objects.equals(a.getMinValue(), b.getMinValue())
                && Objects.equals(a.getTargetValue(), b.getTargetValue())
                && Objects.equals(a.getMaxValue(), b.getMaxValue());
    }

    private static int parseInt(String value, String column) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(column + " не число");
        }
    }

    private static Double parseDouble(String value) {
        String trimmed = value.trim().replace(',', '.');
        if (trimmed.isEmpty()) return null;
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + value.trim() + "' не число");
        }
    }

    private static String required(String value, String column) {
        if (value.isBlank()) throw new IllegalArgumentException("пустая колонка " + column);
        return value.trim();
    }

    private static String format(Double value) {
        if (value == null) return "";
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}
