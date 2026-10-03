package com.broiler_monitoring.Controller;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(roles = "TECHNOLOGIST")
class TaskControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String NEW_TASK = """
            {
              "nameTask": "Проверить вентиляцию",
              "descriptionTask": "Температура выше нормы в птичнике 4",
              "nameIndicator": "Температура",
              "valueIndicator": "34.5",
              "measure": "C",
              "priority": "HIGH",
              "responsible": "Иванов",
              "status": "NEW",
              "termTask": "2030-01-01T12:00:00"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void patchChangesOnlyGivenFields() throws Exception {
        String id = createTask();

        mvc.perform(patch("/api/v1/task/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("IN_PROGRESS")))
                .andExpect(jsonPath("$.priority", equalTo("HIGH")))
                .andExpect(jsonPath("$.responsible", equalTo("Иванов")));
    }

    @Test
    void patchRejectsBlankValues() throws Exception {
        String id = createTask();

        mvc.perform(patch("/api/v1/task/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsible\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesTask() throws Exception {
        String id = createTask();

        mvc.perform(delete("/api/v1/task/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/task/" + id)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/task/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
    }

    private String createTask() throws Exception {
        String body = mvc.perform(post("/api/v1/task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_TASK))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
