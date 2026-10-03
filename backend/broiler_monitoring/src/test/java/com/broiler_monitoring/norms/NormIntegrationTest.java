package com.broiler_monitoring.norms;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S3-01, S3-02: справочник норм загружается при старте, правится с версиями и импортируется из CSV. */
class NormIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void seededNormsAreLookedUpByBreedAndAge() throws Exception {
        Api admin = Api.admin(mvc);
        admin.get("/api/v1/norms?metric=TEMPERATURE&breedCode=ROSS_308").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(30)));
        // Норма конкретного кросса и общая норма «для всех кроссов»
        admin.get("/api/v1/norms/lookup?metric=TEMPERATURE&breedCode=ROSS_308&ageDay=0").andExpect(status().isOk())
                .andExpect(jsonPath("$.breedCode").value("ROSS_308"))
                .andExpect(jsonPath("$.minValue").value(29.0))
                .andExpect(jsonPath("$.maxValue").value(31.0));
        admin.get("/api/v1/norms/lookup?metric=CO2&breedCode=COBB_500&ageDay=20").andExpect(status().isOk())
                .andExpect(jsonPath("$.breedCode").doesNotExist())
                .andExpect(jsonPath("$.maxValue").value(2500.0));
        admin.get("/api/v1/norms/lookup?metric=TEMPERATURE&breedCode=ROSS_308&ageDay=90").andExpect(status().isNoContent());
        admin.get("/api/v1/rules").andExpect(status().isOk()).andExpect(jsonPath("$.length()", greaterThan(5)));
    }

    @Test
    void editingNormCreatesNewVersionWithHistoryAndAudit() throws Exception {
        Api admin = Api.admin(mvc);
        Api tech = admin.newUser("TECHNOLOGIST");
        String id = Api.read(admin.get("/api/v1/norms/lookup?metric=AMMONIA&breedCode=ROSS_308&ageDay=10"), "$.id");

        ResultActions updated = tech.put("/api/v1/norms/" + id, """
                {"maxValue":15,"comment":"Согласовано с технологом площадки"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.maxValue").value(15.0))
                .andExpect(jsonPath("$.previousId").value(id));
        String newId = Api.read(updated, "$.id");

        tech.get("/api/v1/norms/" + newId + "/history").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].maxValue").value(10.0));
        tech.get("/api/v1/norms/lookup?metric=AMMONIA&breedCode=ROSS_308&ageDay=10")
                .andExpect(jsonPath("$.id").value(newId));
        // Старую версию править нельзя
        tech.put("/api/v1/norms/" + id, "{\"maxValue\":12,\"comment\":\"x\"}").andExpect(status().isConflict());
        // Без причины правка не принимается
        tech.put("/api/v1/norms/" + newId, "{\"maxValue\":12}").andExpect(status().isBadRequest());
        tech.put("/api/v1/norms/" + newId, "{\"minValue\":20,\"maxValue\":12,\"comment\":\"x\"}").andExpect(status().isBadRequest());

        admin.get("/api/v1/audit?entityType=NORM&entityId=" + newId).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].summary", containsString("Аммиак")));
        admin.get("/api/v1/audit?entityType=NORM&entityId=" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changes", containsString("Максимум: 10 → 15")));

        // Вернуть как было, чтобы не влиять на другие тесты
        tech.put("/api/v1/norms/" + newId, "{\"maxValue\":10,\"comment\":\"Откат после теста\"}").andExpect(status().isOk());
    }

    @Test
    void onlyTechnologistAndAdminEditNorms() throws Exception {
        Api admin = Api.admin(mvc);
        String id = Api.read(admin.get("/api/v1/norms/lookup?metric=CO2&breedCode=ROSS_308&ageDay=5"), "$.id");
        for (String role : new String[]{"OPERATOR", "VETERINARIAN", "MANAGER"}) {
            Api user = admin.newUser(role);
            user.get("/api/v1/norms").andExpect(status().isOk());
            user.put("/api/v1/norms/" + id, "{\"maxValue\":3000,\"comment\":\"x\"}").andExpect(status().isForbidden());
            user.put("/api/v1/rules/SENSOR_NO_DATA", """
                    {"warnMinutes":45,"criticalMinutes":0,"clearMinutes":0,"enabled":true}
                    """).andExpect(status().isForbidden());
        }
    }

    @Test
    void csvImportAddsAndVersionsNorms() throws Exception {
        Api admin = Api.admin(mvc);
        String csv = """
                breed;metric;age_from;age_to;min;target;max;unit;source
                COBB_500;LIGHT_HOURS;43;56;18;19;20;h;Тест импорта
                ;CO2;0;42;;;2400;ppm;Тест импорта
                XYZ;CO2;0;42;;;2400;ppm;Неизвестный кросс
                """;
        mvc.perform(multipart("/api/v1/norms/import")
                        .file(new MockMultipartFile("file", "norms.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("comment", "Импорт в тесте")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]", containsString("XYZ")));
        admin.get("/api/v1/norms/lookup?metric=CO2&breedCode=ROSS_308&ageDay=1").andExpect(jsonPath("$.maxValue").value(2400.0))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(multipart("/api/v1/norms/import")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv", "a;b\n1;2".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isBadRequest());

        admin.get("/api/v1/norms/export").andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString(";CO2;0;42;;;2400;ppm;")));

        // Вернуть норму CO2 из S1-03
        String co2 = Api.read(admin.get("/api/v1/norms/lookup?metric=CO2&breedCode=ROSS_308&ageDay=1"), "$.id");
        admin.put("/api/v1/norms/" + co2, "{\"maxValue\":2500,\"comment\":\"Откат после теста\"}").andExpect(status().isOk());
    }
}
