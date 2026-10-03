package com.broiler_monitoring.production;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S2-07: датчик переносится в другую зону без правки кода; посевные датчики уже привязаны к «Птичнику 4». */
class SensorLocationIntegrationTest extends AbstractIntegrationTest {

    private static final String HOUSE_4 = "b0000000-0000-0000-0000-000000000004";
    private static final String ZONE_1 = "c0000000-0000-0000-0000-000000000401";
    private static final String ZONE_3 = "c0000000-0000-0000-0000-000000000403";

    @Autowired
    private MockMvc mvc;

    @Test
    void seededSensorsAreBoundToHouseAndZone() throws Exception {
        Api admin = Api.admin(mvc);
        admin.get("/api/v1/sensors/code/LIGHT-03")
                .andExpect(jsonPath("$.houseId", equalTo(HOUSE_4)))
                .andExpect(jsonPath("$.zoneId", equalTo(ZONE_3)));
        admin.get("/api/v1/sensors/code/TEMP-HOUSE-4-01")
                .andExpect(jsonPath("$.zoneId", equalTo(ZONE_1)));
    }

    @Test
    void sensorMovesToAnotherHouseAndZone() throws Exception {
        Api admin = Api.admin(mvc);
        String sensorId = admin.create("/api/v1/sensors", """
                {"code":"CO2-MOVE-TEST","name":"CO2 тест","type":"CO2","farm":"Ферма 1","building":"Птичник 4","unit":"ppm","active":true}
                """);
        String otherHouse = admin.newHouse();
        String otherZone = admin.create("/api/v1/houses/" + otherHouse + "/zones", """
                {"code":"Z1","name":"Зона у входа"}
                """);

        admin.patch("/api/v1/sensors/" + sensorId + "/location", """
                {"houseId":"%s","zoneId":"%s"}
                """.formatted(HOUSE_4, otherZone)).andExpect(status().isBadRequest());

        admin.patch("/api/v1/sensors/" + sensorId + "/location", """
                {"zoneId":"%s"}
                """.formatted(otherZone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseId", equalTo(otherHouse)))
                .andExpect(jsonPath("$.zoneId", equalTo(otherZone)))
                .andExpect(jsonPath("$.building", org.hamcrest.Matchers.startsWith("Тестовый птичник")));

        admin.get("/api/v1/audit?entityType=SENSOR&entityId=" + sensorId)
                .andExpect(jsonPath("$[0].action", equalTo("MOVED")));
    }
}
