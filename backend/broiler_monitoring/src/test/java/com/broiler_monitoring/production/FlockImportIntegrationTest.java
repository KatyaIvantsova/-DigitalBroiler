package com.broiler_monitoring.production;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S4-07: импорт ежедневного учёта из CSV учётной системы. */
class FlockImportIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Samara"));
    private static final DateTimeFormatter RU = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String HEADER = "date;mortality;culled;feed_kg;water_l;weight_g;sample_heads;comment\n";

    @Autowired
    private MockMvc mvc;

    private Api admin;
    private String houseId;
    private String flockId;

    @BeforeEach
    void setUp() throws Exception {
        admin = Api.admin(mvc);
        houseId = admin.newHouse();
        flockId = admin.create("/api/v1/flocks", """
                {"houseId":"%s","breedCode":"ROSS_308","placedAt":"%s","placedHeads":20000,"placedAvgWeightG":42}
                """.formatted(houseId, TODAY.minusDays(10)));
    }

    @Test
    void importsRecordsAndWeighingsAndUpdatesOnRepeat() throws Exception {
        String csv = HEADER
                + "%s;12;3;1450,5;2610;;;\n".formatted(TODAY.minusDays(8).format(RU))
                + "%s;10;2;1530;2750;198;100;взвешивание в зоне 2\n".formatted(TODAY.minusDays(7));
        upload(admin, csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied", equalTo(true)))
                .andExpect(jsonPath("$.created", equalTo(2)))
                .andExpect(jsonPath("$.weighings", equalTo(1)));

        admin.get("/api/v1/flocks/" + flockId + "/daily-records")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].feedConsumedKg", equalTo(1450.5)))
                .andExpect(jsonPath("$[1].comment", equalTo("взвешивание в зоне 2")));
        admin.get("/api/v1/flocks/" + flockId + "/weighings")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ageDay", equalTo(3)));

        String corrected = HEADER
                + "%s;12;3;1450,5;2610;;;\n".formatted(TODAY.minusDays(8).format(RU))
                + "%s;11;2;1530;2750;198;100;взвешивание в зоне 2\n".formatted(TODAY.minusDays(7));
        upload(admin, corrected.getBytes(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.applied", equalTo(true)))
                .andExpect(jsonPath("$.created", equalTo(0)))
                .andExpect(jsonPath("$.updated", equalTo(1)))
                .andExpect(jsonPath("$.unchanged", equalTo(1)))
                .andExpect(jsonPath("$.weighings", equalTo(0)));
        admin.get("/api/v1/flocks/" + flockId + "/history")
                .andExpect(jsonPath("$[*].action", hasItem("DAILY_RECORD_UPDATED")));
    }

    @Test
    void fileWithErrorsSavesNothing() throws Exception {
        String csv = HEADER
                + "%s;5;0;1000;;;;\n".formatted(TODAY.minusDays(5).format(RU))
                + "%s;пять;0;;;;;\n".formatted(TODAY.minusDays(4).format(RU))
                + "%s;1;0;;;;;\n".formatted(TODAY.plusDays(3).format(RU));
        upload(admin, csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied", equalTo(false)))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0]", containsString("Строка 3")))
                .andExpect(jsonPath("$.errors[1]", containsString("вне периода партии")));
        admin.get("/api/v1/flocks/" + flockId + "/daily-records").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void readsWindows1251ExportAndRejectsWrongHeader() throws Exception {
        String csv = "date;mortality;culled;comment\n%s;4;1;выгрузка из 1С\n".formatted(TODAY.minusDays(2));
        upload(admin, csv.getBytes(Charset.forName("windows-1251")))
                .andExpect(jsonPath("$.applied", equalTo(true)));
        admin.get("/api/v1/flocks/" + flockId + "/daily-records")
                .andExpect(jsonPath("$[*].comment", contains("выгрузка из 1С")));

        upload(admin, "дата;падёж\n01.01.2026;1\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
    }

    @Test
    void operatorCannotImport() throws Exception {
        Api operator = admin.newUser("OPERATOR", houseId);
        upload(operator, (HEADER + "%s;1;0;;;;;\n".formatted(TODAY.minusDays(1))).getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isForbidden());
    }

    private ResultActions upload(Api api, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/flocks/" + flockId + "/import")
                .file(new MockMultipartFile("file", "uchet.csv", "text/csv", content))
                .header("Authorization", "Bearer " + api.token()));
    }
}
