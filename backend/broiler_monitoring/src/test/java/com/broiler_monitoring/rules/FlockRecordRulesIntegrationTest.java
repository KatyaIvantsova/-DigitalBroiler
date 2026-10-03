package com.broiler_monitoring.rules;

import com.broiler_monitoring.entity.Incident;
import com.broiler_monitoring.enumerated.IncidentPriority;
import com.broiler_monitoring.enumerated.IncidentSource;
import com.broiler_monitoring.enumerated.IncidentStatus;
import com.broiler_monitoring.enumerated.IncidentType;
import com.broiler_monitoring.repository.IncidentRepository;
import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S4-05: правила по ежедневному учёту — падёж выше нормы, падение корма и воды, закрытие после исправления учёта. */
class FlockRecordRulesIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Samara"));
    /** Возраст 8–28 дней: норма падежа Ross 308 — до 0,07 % в сутки. */
    private static final LocalDate PLACED = TODAY.minusDays(12);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IncidentRepository incidents;

    private Api admin;
    private String flockId;

    @BeforeEach
    void setUp() throws Exception {
        admin = Api.admin(mvc);
        String houseId = admin.newHouse();
        flockId = admin.create("/api/v1/flocks", """
                {"houseId":"%s","breedCode":"ROSS_308","placedAt":"%s","placedHeads":20000,"placedAvgWeightG":42}
                """.formatted(houseId, PLACED));
    }

    @Test
    void mortalityAboveNormRaisesIncidentAndCorrectionResolvesIt() throws Exception {
        LocalDate day9 = PLACED.plusDays(9);
        record(day9, 10, 2, 1000, 1800); // 0,06 % — в норме
        assertThat(open("FLOCK_MORTALITY_DAILY", day9)).isEmpty();

        String id = record(PLACED.plusDays(10), 20, 2, 1000, 1800); // 0,11 % — выше нормы
        Incident incident = single("FLOCK_MORTALITY_DAILY", PLACED.plusDays(10));
        assertThat(incident.getPriority()).isEqualTo(IncidentPriority.HIGH);
        assertThat(incident.getType()).isEqualTo(IncidentType.FLOCK_HEALTH);
        assertThat(incident.getSource()).isEqualTo(IncidentSource.SYSTEM);
        assertThat(incident.getDescription()).contains("22 гол").contains("0,07 %");

        update(id, PLACED.plusDays(10), 40, 5, 1000, 1800); // 0,23 % — больше двух норм
        assertThat(incidents.findById(incident.getId()).orElseThrow().getPriority()).isEqualTo(IncidentPriority.CRITICAL);
        assertThat(open("FLOCK_MORTALITY_DAILY", PLACED.plusDays(10))).hasSize(1);

        update(id, PLACED.plusDays(10), 8, 2, 1000, 1800); // исправили опечатку
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void feedAndWaterDropAgainstPreviousDay() throws Exception {
        record(PLACED.plusDays(9), 5, 0, 1000, 1800);
        String id = record(PLACED.plusDays(10), 5, 0, 850, 1300); // корм −15 %, вода −27,8 %

        Incident feed = single("FEED_DROP", PLACED.plusDays(10));
        assertThat(feed.getPriority()).isEqualTo(IncidentPriority.HIGH);
        assertThat(feed.getType()).isEqualTo(IncidentType.FEEDING);
        assertThat(feed.getDescription()).contains("на 15,0 % меньше");
        Incident water = single("WATER_DROP", PLACED.plusDays(10));
        assertThat(water.getPriority()).isEqualTo(IncidentPriority.CRITICAL);
        assertThat(water.getType()).isEqualTo(IncidentType.WATER_SUPPLY);

        update(id, PLACED.plusDays(10), 5, 0, 990, 1790);
        assertThat(incidents.findById(feed.getId()).orElseThrow().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incidents.findById(water.getId()).orElseThrow().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void disabledRuleDoesNotFire() throws Exception {
        admin.put("/api/v1/rules/FEED_DROP", """
                {"warnMinutes":0,"warnDelta":10,"criticalDelta":20,"criticalMinutes":0,"clearMinutes":0,"enabled":false}
                """).andExpect(status().isOk());
        try {
            record(PLACED.plusDays(9), 5, 0, 1000, 1800);
            record(PLACED.plusDays(10), 5, 0, 500, 1800);
            assertThat(open("FEED_DROP", PLACED.plusDays(10))).isEmpty();
        } finally {
            admin.put("/api/v1/rules/FEED_DROP", """
                    {"warnMinutes":0,"warnDelta":10,"criticalDelta":20,"criticalMinutes":0,"clearMinutes":0,"enabled":true}
                    """).andExpect(status().isOk());
        }
    }

    private List<Incident> open(String rule, LocalDate date) {
        return incidents.findOpenByDedupKey(rule + ":" + flockId + ":" + date);
    }

    private Incident single(String rule, LocalDate date) {
        List<Incident> found = open(rule, date);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private String record(LocalDate date, int mortality, int culled, double feed, double water) throws Exception {
        return admin.create("/api/v1/flocks/" + flockId + "/daily-records", json(date, mortality, culled, feed, water));
    }

    private void update(String id, LocalDate date, int mortality, int culled, double feed, double water) throws Exception {
        admin.put("/api/v1/flocks/" + flockId + "/daily-records/" + id, json(date, mortality, culled, feed, water))
                .andExpect(status().isOk());
    }

    private static String json(LocalDate date, int mortality, int culled, double feed, double water) {
        return """
                {"recordDate":"%s","mortalityHeads":%d,"culledHeads":%d,"feedConsumedKg":%s,"waterConsumedL":%s}
                """.formatted(date, mortality, culled, feed, water);
    }
}
