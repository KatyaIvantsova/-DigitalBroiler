package com.broiler_monitoring.production;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S2-01…S2-03: партия от посадки до закрытия, ежедневный учёт с историей правок. */
class FlockIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Samara"));

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
    void flockLifecycleFromPlacementToClosing() throws Exception {
        String flockId = placeFlock(TODAY.minusDays(10), 20000);

        admin.get("/api/v1/flocks/" + flockId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.ageDays", equalTo(10)))
                .andExpect(jsonPath("$.currentHeads", equalTo(20000)));

        dailyRecord(flockId, TODAY.minusDays(9), 30, 5).andExpect(status().isCreated());
        dailyRecord(flockId, TODAY.minusDays(8), 20, 0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ageDay", equalTo(2)))
                .andExpect(jsonPath("$.headsAtEnd", equalTo(19945)));

        admin.post("/api/v1/flocks/" + flockId + "/weighings", """
                        {"sampleHeads":100,"avgWeightG":310.5,"uniformityPct":82}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ageDay", equalTo(10)));

        admin.get("/api/v1/flocks/" + flockId)
                .andExpect(jsonPath("$.mortalityTotal", equalTo(50)))
                .andExpect(jsonPath("$.culledTotal", equalTo(5)))
                .andExpect(jsonPath("$.currentHeads", equalTo(19945)));

        admin.post("/api/v1/flocks/" + flockId + "/close", """
                        {"closedAt":"%s","shippedHeads":19945,"shippedLiveWeightKg":51000}
                        """.formatted(TODAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CLOSED")))
                .andExpect(jsonPath("$.ageDays", equalTo(10)));

        admin.get("/api/v1/flocks/" + flockId + "/history")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("CREATED")))
                .andExpect(jsonPath("$[*].action", hasItem("DAILY_RECORD_CREATED")))
                .andExpect(jsonPath("$[*].action", hasItem("WEIGHING_CREATED")))
                .andExpect(jsonPath("$[0].action", equalTo("CLOSED")));
    }

    @Test
    void houseHoldsOnlyOneActiveFlock() throws Exception {
        placeFlock(TODAY.minusDays(3), 10000);

        admin.post("/api/v1/flocks", flockJson(TODAY.minusDays(1), 10000)).andExpect(status().isConflict());
        // Будущая посадка в занятый птичник разрешена — она остаётся запланированной
        admin.post("/api/v1/flocks", flockJson(TODAY.plusDays(30), 10000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", equalTo("PLANNED")))
                .andExpect(jsonPath("$.ageDays").doesNotExist());
    }

    @Test
    void dailyRecordEditIsLoggedWithOldAndNewValues() throws Exception {
        String flockId = placeFlock(TODAY.minusDays(5), 10000);
        String recordId = Api.read(dailyRecord(flockId, TODAY.minusDays(1), 12, 3).andExpect(status().isCreated()), "$.id");

        admin.put("/api/v1/flocks/" + flockId + "/daily-records/" + recordId, """
                        {"recordDate":"%s","mortalityHeads":15,"culledHeads":3,"feedConsumedKg":410.5,"comment":"пересчёт"}
                        """.formatted(TODAY.minusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mortalityHeads", equalTo(15)));

        admin.get("/api/v1/flocks/" + flockId + "/history")
                .andExpect(jsonPath("$[0].action", equalTo("DAILY_RECORD_UPDATED")))
                .andExpect(jsonPath("$[0].actorName", equalTo("Администратор")))
                .andExpect(jsonPath("$[0].changes", containsString("Падёж, гол: 12 → 15")))
                .andExpect(jsonPath("$[0].changes", containsString("Корм, кг: 350 → 410.5")));
    }

    @Test
    void dailyRecordValidation() throws Exception {
        String flockId = placeFlock(TODAY.minusDays(5), 100);

        dailyRecord(flockId, TODAY.minusDays(2), 1, 0).andExpect(status().isCreated());
        // Повтор за ту же дату
        dailyRecord(flockId, TODAY.minusDays(2), 1, 0).andExpect(status().isConflict());
        // До посадки и в будущем
        dailyRecord(flockId, TODAY.minusDays(6), 1, 0).andExpect(status().isBadRequest());
        dailyRecord(flockId, TODAY.plusDays(1), 1, 0).andExpect(status().isBadRequest());
        // Списано больше, чем посажено
        dailyRecord(flockId, TODAY.minusDays(1), 90, 20).andExpect(status().isBadRequest());
        // Отрицательные значения
        dailyRecord(flockId, TODAY.minusDays(1), -1, 0).andExpect(status().isBadRequest());

        admin.get("/api/v1/flocks/" + flockId + "/daily-records").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void operatorWorksOnlyWithAssignedHouses() throws Exception {
        String flockId = placeFlock(TODAY.minusDays(4), 5000);
        Api assigned = admin.newUser("OPERATOR", houseId);
        Api stranger = admin.newUser("OPERATOR");

        assigned.get("/api/v1/flocks").andExpect(jsonPath("$[*].id", hasItem(flockId)));
        stranger.get("/api/v1/flocks").andExpect(jsonPath("$", hasSize(0)));
        stranger.get("/api/v1/flocks/" + flockId).andExpect(status().isForbidden());

        assigned.post("/api/v1/flocks/" + flockId + "/daily-records", recordJson(TODAY, 2, 0)).andExpect(status().isCreated());
        stranger.post("/api/v1/flocks/" + flockId + "/daily-records", recordJson(TODAY.minusDays(1), 2, 0))
                .andExpect(status().isForbidden());

        // Оператор не закрывает партию и не правит закрытую
        assigned.post("/api/v1/flocks/" + flockId + "/close", """
                {"closedAt":"%s","shippedHeads":4990,"shippedLiveWeightKg":9000}
                """.formatted(TODAY)).andExpect(status().isForbidden());
        admin.post("/api/v1/flocks/" + flockId + "/close", """
                {"closedAt":"%s","shippedHeads":4990,"shippedLiveWeightKg":9000}
                """.formatted(TODAY)).andExpect(status().isOk());
        assigned.post("/api/v1/flocks/" + flockId + "/daily-records", recordJson(TODAY.minusDays(1), 1, 0))
                .andExpect(status().isForbidden());
        admin.post("/api/v1/flocks/" + flockId + "/daily-records", recordJson(TODAY.minusDays(1), 1, 0))
                .andExpect(status().isCreated());
    }

    @Test
    void closingRequiresConsistentNumbers() throws Exception {
        String flockId = placeFlock(TODAY.minusDays(40), 1000);
        dailyRecord(flockId, TODAY.minusDays(30), 10, 0).andExpect(status().isCreated());

        admin.post("/api/v1/flocks/" + flockId + "/close", """
                {"closedAt":"%s","shippedHeads":995,"shippedLiveWeightKg":2500}
                """.formatted(TODAY)).andExpect(status().isBadRequest());
        admin.post("/api/v1/flocks/" + flockId + "/close", """
                {"closedAt":"%s","shippedHeads":990,"shippedLiveWeightKg":2500}
                """.formatted(TODAY.minusDays(41))).andExpect(status().isBadRequest());
    }

    private String placeFlock(LocalDate placedAt, int heads) throws Exception {
        return admin.create("/api/v1/flocks", flockJson(placedAt, heads));
    }

    private String flockJson(LocalDate placedAt, int heads) {
        return """
                {"houseId":"%s","breedCode":"ROSS_308","placedAt":"%s","placedHeads":%d,"placedAvgWeightG":42,"targetAgeDays":40}
                """.formatted(houseId, placedAt, heads);
    }

    private org.springframework.test.web.servlet.ResultActions dailyRecord(String flockId, LocalDate date, int mortality, int culled)
            throws Exception {
        return admin.post("/api/v1/flocks/" + flockId + "/daily-records", recordJson(date, mortality, culled));
    }

    private static String recordJson(LocalDate date, int mortality, int culled) {
        return """
                {"recordDate":"%s","mortalityHeads":%d,"culledHeads":%d,"feedConsumedKg":350,"waterConsumedL":700}
                """.formatted(date, mortality, culled);
    }
}
