package com.broiler_monitoring.security;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import com.broiler_monitoring.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S2-05: администратор создаёт пользователя, меняет роль, блокирует; S2-06: действия попадают в журнал. */
class UserAdminIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void adminCreatesUserWhoCanLogIn() throws Exception {
        Api admin = Api.admin(mvc);
        String id = admin.create("/api/v1/users", """
                {"username":"tech.ivanova","fullName":"Иванова Анна","position":"Зоотехник","role":"TECHNOLOGIST","password":"secret-pass-1"}
                """);

        Api tech = Api.login(mvc, "tech.ivanova", "secret-pass-1");
        tech.get("/api/v1/auth/me")
                .andExpect(jsonPath("$.role", equalTo("TECHNOLOGIST")))
                .andExpect(jsonPath("$.position", equalTo("Зоотехник")));
        tech.get("/api/v1/users").andExpect(status().isForbidden());

        // Блокировка: вход больше не работает
        admin.put("/api/v1/users/" + id, """
                {"username":"tech.ivanova","fullName":"Иванова Анна","position":"Зоотехник","role":"TECHNOLOGIST","enabled":false}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.enabled", equalTo(false)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tech.ivanova\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isUnauthorized());

        admin.get("/api/v1/audit?entityType=USER&entityId=" + id)
                .andExpect(jsonPath("$[*].action", hasItem("CREATED")))
                .andExpect(jsonPath("$[0].changes", org.hamcrest.Matchers.containsString("Активен: true → false")));
    }

    @Test
    void duplicateLoginAndShortPasswordAreRejected() throws Exception {
        Api admin = Api.admin(mvc);
        admin.post("/api/v1/users", """
                {"username":"admin","fullName":"Дубль","position":"—","role":"OPERATOR","password":"password-123"}
                """).andExpect(status().isConflict());
        admin.post("/api/v1/users", """
                {"username":"short.pass","fullName":"Короткий","position":"—","role":"OPERATOR","password":"123"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotDemoteThemselves() throws Exception {
        Api admin = Api.admin(mvc);
        String myId = Api.read(admin.get("/api/v1/auth/me"), "$.id");
        admin.put("/api/v1/users/" + myId, """
                {"username":"admin","fullName":"Администратор","position":"ИТ","role":"OPERATOR"}
                """).andExpect(status().isBadRequest());
    }
}
