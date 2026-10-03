package com.broiler_monitoring.kpi;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S4-01, S4-02: KPI партии по данным учёта и план-факт по кривой кросса. */
class KpiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Samara");
    private static final LocalDate TODAY = LocalDate.now(ZONE);

    @Autowired
    private MockMvc mvc;

    private Api admin;
    private String houseId;

    @BeforeEach
    void setUp() throws Exception {
        admin = Api.admin(mvc);
        houseId = admin.newHouse();
    }

    @Test
    void closedFlockMatchesReferenceExampleS104() throws Exception {
        String flockId = placeFlock(TODAY.minusDays(40));
        record(flockId, TODAY.minusDays(39), 300, 100, 41000);
        record(flockId, TODAY.minusDays(38), 220, 80, 41000);
        admin.post("/api/v1/flocks/" + flockId + "/close", """
                {"closedAt":"%s","shippedHeads":19300,"shippedLiveWeightKg":50180}
                """.formatted(TODAY)).andExpect(status().isOk());

        admin.get("/api/v1/flocks/" + flockId + "/kpi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed", equalTo(true)))
                .andExpect(jsonPath("$.heads", equalTo(19300)))
                .andExpect(jsonPath("$.survivalPct", equalTo(96.5)))
                .andExpect(jsonPath("$.lossPct", equalTo(3.5)))
                .andExpect(jsonPath("$.fcr", equalTo(1.634)))
                .andExpect(jsonPath("$.adgG", equalTo(64.0)))
                .andExpect(jsonPath("$.epef", equalTo(384.0)))
                .andExpect(jsonPath("$.weightAgeDays", equalTo(40)))
                .andExpect(jsonPath("$.unaccountedHeads", nullValue()))
                .andExpect(jsonPath("$.normWeightG", notNullValue()));
    }

    @Test
    void activeFlockUsesLastWeighingAndShowsPlanFact() throws Exception {
        String flockId = placeFlock(TODAY.minusDays(14));
        for (int day = 1; day <= 14; day++) {
            record(flockId, TODAY.minusDays(14 - day), 5, 1, 1000);
        }
        weigh(flockId, TODAY.minusDays(7), 180);
        weigh(flockId, TODAY, 500);

        admin.get("/api/v1/flocks/" + flockId + "/kpi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed", equalTo(false)))
                .andExpect(jsonPath("$.ageDays", equalTo(14)))
                .andExpect(jsonPath("$.heads", equalTo(19916)))
                .andExpect(jsonPath("$.survivalPct", equalTo(99.58)))
                .andExpect(jsonPath("$.avgWeightG", equalTo(500.0)))
                .andExpect(jsonPath("$.weightAgeDays", equalTo(14)))
                .andExpect(jsonPath("$.feedKg", equalTo(14000.0)))
                .andExpect(jsonPath("$.feedMissingDays", equalTo(0)))
                .andExpect(jsonPath("$.fcr", equalTo(1.406)))
                .andExpect(jsonPath("$.adgG", equalTo(32.7)))
                .andExpect(jsonPath("$.normFcr", notNullValue()))
                .andExpect(jsonPath("$.weightDeviationPct", notNullValue()));

        admin.get("/api/v1/flocks/" + flockId + "/plan-fact")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breedCode", equalTo("ROSS_308")))
                .andExpect(jsonPath("$.weighings", hasSize(2)))
                .andExpect(jsonPath("$.weighings[0].ageDay", equalTo(7)))
                .andExpect(jsonPath("$.weighings[0].normWeightG", greaterThan(0.0)))
                .andExpect(jsonPath("$.weighings[0].weightDeviationG", notNullValue()))
                .andExpect(jsonPath("$.weighings[0].fcr", closeTo(7000.0 / (19958 * 0.18), 0.001)))
                .andExpect(jsonPath("$.curve", hasSize(41)))
                .andExpect(jsonPath("$.curve[0].ageDay", equalTo(0)));
    }

    @Test
    void withoutWeighingsPerformanceIsNotCalculated() throws Exception {
        String flockId = placeFlock(TODAY.minusDays(5));
        record(flockId, TODAY.minusDays(4), 3, 0, 500);

        admin.get("/api/v1/flocks/" + flockId + "/kpi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heads", equalTo(19997)))
                .andExpect(jsonPath("$.avgWeightG", nullValue()))
                .andExpect(jsonPath("$.fcr", nullValue()))
                .andExpect(jsonPath("$.adgG", nullValue()))
                .andExpect(jsonPath("$.epef", nullValue()));
    }

    private String placeFlock(LocalDate placedAt) throws Exception {
        return admin.create("/api/v1/flocks", """
                {"houseId":"%s","breedCode":"ROSS_308","placedAt":"%s","placedHeads":20000,"placedAvgWeightG":42,"targetAgeDays":40}
                """.formatted(houseId, placedAt));
    }

    private void record(String flockId, LocalDate date, int mortality, int culled, double feedKg) throws Exception {
        admin.post("/api/v1/flocks/" + flockId + "/daily-records", """
                {"recordDate":"%s","mortalityHeads":%d,"culledHeads":%d,"feedConsumedKg":%s}
                """.formatted(date, mortality, culled, feedKg)).andExpect(status().isCreated());
    }

    private void weigh(String flockId, LocalDate date, double grams) throws Exception {
        admin.post("/api/v1/flocks/" + flockId + "/weighings", """
                {"weighedAt":"%s","sampleHeads":100,"avgWeightG":%s}
                """.formatted(date.atTime(7, 0).atZone(ZONE).toInstant(), grams)).andExpect(status().isCreated());
    }
}
