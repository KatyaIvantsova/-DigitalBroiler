package com.broiler_monitoring.kpi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** S3-08: формулы KPI дают эталонные ответы на наборе партий src/test/resources/kpi/reference-flocks.json. */
class KpiFormulasTest {

    static Stream<Arguments> referenceFlocks() throws IOException {
        try (InputStream input = KpiFormulasTest.class.getResourceAsStream("/kpi/reference-flocks.json")) {
            JsonNode root = new ObjectMapper().readTree(input);
            List<Arguments> cases = new ArrayList<>();
            root.get("flocks").forEach(flock -> cases.add(Arguments.of(flock.get("name").asText(), flock.get("input"), flock.get("expected"))));
            return cases.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("referenceFlocks")
    void matchesReferenceAnswers(String name, JsonNode in, JsonNode expected) {
        KpiFormulas.Result result = KpiFormulas.calculate(new KpiFormulas.Input(
                in.get("placedHeads").asInt(),
                number(in, "placedAvgWeightG"),
                in.get("mortality").asInt(),
                in.get("culled").asInt(),
                in.get("shippedHeads").isNull() ? null : in.get("shippedHeads").asInt(),
                number(in, "avgWeightKg"),
                in.get("ageDays").asInt(),
                number(in, "feedKg")));

        assertThat(result.heads()).isEqualTo(expected.get("heads").asInt());
        assertThat(KpiFormulas.round(result.survivalPct(), 2)).isEqualTo(expected.get("survivalPct").asDouble());
        assertThat(KpiFormulas.round(result.lossPct(), 2)).isEqualTo(expected.get("lossPct").asDouble());
        assertRounded(result.liveMassKg(), expected, "liveMassKg", 1);
        assertRounded(result.fcr(), expected, "fcr", 3);
        assertRounded(result.adgG(), expected, "adgG", 1);
        assertRounded(result.epef(), expected, "epef", 0);
        assertThat(result.cycleDays()).isEqualTo(expected.get("cycleDays").asInt());
    }

    private static Double number(JsonNode node, String field) {
        return node.get(field).isNull() ? null : node.get(field).asDouble();
    }

    private static void assertRounded(Double actual, JsonNode expected, String field, int digits) {
        if (expected.get(field).isNull()) {
            assertThat(actual).as(field + ": нет данных").isNull();
        } else {
            assertThat(actual).as(field).isNotNull();
            assertThat(KpiFormulas.round(actual, digits)).as(field).isEqualTo(expected.get(field).asDouble());
        }
    }
}
