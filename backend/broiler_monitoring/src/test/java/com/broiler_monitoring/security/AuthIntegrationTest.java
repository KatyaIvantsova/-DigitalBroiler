package com.broiler_monitoring.security;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/incidents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/task")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notification")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapAdminCanLoginAndCallApi() throws Exception {
        String token = loginAsAdmin();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", equalTo(ADMIN_USERNAME)))
                .andExpect(jsonPath("$.role", equalTo("ADMIN")));

        mvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestApiKeyAllowsOnlyTelemetryWrite() throws Exception {
        String payload = "{\"gatewayId\":\"GW-TEST\",\"readings\":[{\"sensorCode\":\"TEMP-HOUSE-4-01\","
                + "\"type\":\"TEMPERATURE\",\"value\":33.1,\"unit\":\"C\",\"measuredAt\":\"" + Instant.now() + "\"}]}";

        mvc.perform(post("/api/v1/telemetry/readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/telemetry/readings")
                        .header(TelemetryApiKeyFilter.HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/telemetry/readings")
                        .header(TelemetryApiKeyFilter.HEADER, INGEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/v1/incidents").header(TelemetryApiKeyFilter.HEADER, INGEST_API_KEY))
                .andExpect(status().isForbidden());
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", equalTo("Bearer")))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
