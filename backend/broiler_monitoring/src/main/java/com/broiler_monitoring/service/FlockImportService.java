package com.broiler_monitoring.service;

import com.broiler_monitoring.dto.production.DailyRecordRequest;
import com.broiler_monitoring.dto.production.FlockImportResult;
import com.broiler_monitoring.dto.production.WeighingRequest;
import com.broiler_monitoring.entity.DailyRecord;
import com.broiler_monitoring.entity.Flock;
import com.broiler_monitoring.enumerated.FlockStatus;
import com.broiler_monitoring.enumerated.WeighingMethod;
import com.broiler_monitoring.repository.DailyRecordRepository;
import com.broiler_monitoring.repository.WeighingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Импорт ежедневного учёта из CSV учётной системы (S4-07, docs/sprint2/09-integration-protocol.md п. 3).
 * Файл проверяется целиком: при любой ошибке ничего не сохраняется. Строка за уже внесённую дату обновляет
 * запись; сохранение идёт через {@link FlockJournalService}, поэтому правки попадают в журнал действий,
 * а правила по учёту (S4-05) проверяются так же, как при ручном вводе.
 */
@Service
public class FlockImportService {

    static final List<String> REQUIRED = List.of("date", "mortality", "culled");
    private static final Set<String> KNOWN = Set.of("date", "mortality", "culled", "feed_kg", "water_l", "weight_g", "sample_heads", "comment");
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    /** Время взвешивания из файла учёта, если в файле только дата. */
    private static final LocalTime WEIGHING_TIME = LocalTime.of(7, 0);

    private final FlockService flocks;
    private final FlockJournalService journal;
    private final DailyRecordRepository dailyRecords;
    private final WeighingRepository weighings;
    private final StructureService structure;

    public FlockImportService(FlockService flocks, FlockJournalService journal, DailyRecordRepository dailyRecords,
                              WeighingRepository weighings, StructureService structure) {
        this.flocks = flocks;
        this.journal = journal;
        this.dailyRecords = dailyRecords;
        this.weighings = weighings;
        this.structure = structure;
    }

    record Row(int line, DailyRecordRequest record, Double weightG, Integer sampleHeads) {
    }

    @Transactional
    public FlockImportResult importCsv(UUID flockId, byte[] content) {
        Flock flock = flocks.getVisible(flockId);
        if (flock.getStatus() == FlockStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Партия ещё не посажена");
        }
        LocalDate last = flock.getClosedAt() != null ? flock.getClosedAt() : flocks.today(flock.getHouseId());
        List<String> errors = new ArrayList<>();
        List<Row> rows = parse(decode(content), flock.getPlacedAt(), last, errors);
        if (!errors.isEmpty()) {
            return new FlockImportResult(false, rows.size() + errors.size(), 0, 0, 0, 0, errors);
        }

        Map<LocalDate, DailyRecord> existing = new HashMap<>();
        dailyRecords.findByFlockIdOrderByRecordDateAsc(flockId).forEach(r -> existing.put(r.getRecordDate(), r));
        ZoneId zone = structure.timezoneOfHouse(flock.getHouseId());
        Set<String> knownWeighings = new HashSet<>();
        weighings.findByFlockIdOrderByWeighedAtAsc(flockId).forEach(w ->
                knownWeighings.add(w.getWeighedAt().atZone(zone).toLocalDate() + ":" + Math.round(w.getAvgWeightG())));

        int created = 0, updated = 0, unchanged = 0, weighed = 0;
        for (Row row : rows) {
            DailyRecordRequest request = row.record();
            DailyRecord current = existing.get(request.recordDate());
            if (current == null) {
                journal.createDailyRecord(flockId, request);
                created++;
            } else if (same(current, request)) {
                unchanged++;
            } else {
                journal.updateDailyRecord(flockId, current.getId(), request);
                updated++;
            }
            if (row.weightG() != null && knownWeighings.add(request.recordDate() + ":" + Math.round(row.weightG()))) {
                journal.createWeighing(flockId, new WeighingRequest(request.recordDate().atTime(WEIGHING_TIME).atZone(zone).toInstant(),
                        null, row.sampleHeads() != null ? row.sampleHeads() : 100, row.weightG(), null, WeighingMethod.MANUAL));
                weighed++;
            }
        }
        return new FlockImportResult(true, rows.size(), created, updated, unchanged, weighed, List.of());
    }

    /** UTF-8 (с BOM или без); если файл не читается как UTF-8 — Windows-1251 (выгрузка из 1С). */
    static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString().replace("﻿", "");
        } catch (CharacterCodingException e) {
            return new String(content, Charset.forName("windows-1251"));
        }
    }

    static List<Row> parse(String csv, LocalDate from, LocalDate to, List<String> errors) {
        String[] lines = csv.split("\\R");
        int headerIndex = 0;
        while (headerIndex < lines.length && lines[headerIndex].isBlank()) {
            headerIndex++;
        }
        if (headerIndex == lines.length) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл пустой");
        }
        String[] header = lines[headerIndex].split(";", -1);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String name = header[i].trim().toLowerCase(Locale.ROOT);
            if (KNOWN.contains(name)) {
                columns.put(name, i);
            }
        }
        List<String> missing = REQUIRED.stream().filter(name -> !columns.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "В первой строке нет колонок: %s. Нужен заголовок: date;mortality;culled;feed_kg;water_l;weight_g;sample_heads;comment"
                            .formatted(String.join(", ", missing)));
        }

        List<Row> rows = new ArrayList<>();
        Set<LocalDate> seen = new HashSet<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            int lineNumber = i + 1;
            String[] cells = lines[i].split(";", -1);
            try {
                LocalDate date = date(cell(cells, columns, "date"));
                if (date == null) {
                    throw new IllegalArgumentException("не указана дата");
                }
                if (date.isBefore(from) || date.isAfter(to)) {
                    throw new IllegalArgumentException("дата %s вне периода партии (%s — %s)".formatted(
                            date.format(RU_DATE), from.format(RU_DATE), to.format(RU_DATE)));
                }
                if (!seen.add(date)) {
                    throw new IllegalArgumentException("дата %s повторяется в файле".formatted(date.format(RU_DATE)));
                }
                Integer mortality = count(cell(cells, columns, "mortality"), "падёж");
                Integer culled = count(cell(cells, columns, "culled"), "выбраковка");
                if (mortality == null || culled == null) {
                    throw new IllegalArgumentException("падёж и выбраковка обязательны (0, если не было)");
                }
                Double feed = amount(cell(cells, columns, "feed_kg"), "корм");
                Double water = amount(cell(cells, columns, "water_l"), "вода");
                Double weight = amount(cell(cells, columns, "weight_g"), "вес");
                if (weight != null && weight == 0) {
                    throw new IllegalArgumentException("вес должен быть больше нуля");
                }
                Integer sample = count(cell(cells, columns, "sample_heads"), "выборка");
                if (sample != null && sample == 0) {
                    throw new IllegalArgumentException("выборка должна быть больше нуля");
                }
                String comment = cell(cells, columns, "comment");
                rows.add(new Row(lineNumber, new DailyRecordRequest(date, mortality, culled, feed, water,
                        comment == null || comment.isBlank() ? null : comment.trim()), weight, sample));
            } catch (IllegalArgumentException e) {
                errors.add("Строка %d: %s".formatted(lineNumber, e.getMessage()));
            }
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В файле нет строк учёта");
        }
        rows.sort(Comparator.comparing(row -> row.record().recordDate()));
        return rows;
    }

    private static String cell(String[] cells, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= cells.length) {
            return null;
        }
        String value = cells[index].trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value.isEmpty() ? null : value;
    }

    static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return value.contains(".") ? LocalDate.parse(value, RU_DATE) : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("дата «%s» не в формате ДД.ММ.ГГГГ или ГГГГ-ММ-ДД".formatted(value));
        }
    }

    private static Integer count(String value, String what) {
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.replace(" ", "").replace(" ", ""));
            if (parsed < 0) {
                throw new IllegalArgumentException("%s не может быть отрицательным".formatted(what));
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("%s «%s» — не целое число".formatted(what, value));
        }
    }

    static Double amount(String value, String what) {
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.replace(" ", "").replace(" ", "").replace(',', '.'));
            if (parsed < 0) {
                throw new IllegalArgumentException("%s не может быть отрицательным".formatted(what));
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("%s «%s» — не число".formatted(what, value));
        }
    }

    private static boolean same(DailyRecord record, DailyRecordRequest request) {
        return record.getMortalityHeads() == request.mortalityHeads()
                && record.getCulledHeads() == request.culledHeads()
                && Objects.equals(record.getFeedConsumedKg(), request.feedConsumedKg())
                && Objects.equals(record.getWaterConsumedL(), request.waterConsumedL())
                && Objects.equals(record.getComment(), request.comment());
    }
}
