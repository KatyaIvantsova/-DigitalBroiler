package com.broiler_monitoring.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Вызовы API от имени настоящего пользователя (JWT из /auth/login) для интеграционных тестов. */
public final class Api {

    public static final String SITE_ID = "a0000000-0000-0000-0000-000000000001";

    private final MockMvc mvc;
    private final String token;

    private Api(MockMvc mvc, String token) {
        this.mvc = mvc;
        this.token = token;
    }

    public static Api login(MockMvc mvc, String username, String password) throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Api(mvc, JsonPath.read(body, "$.accessToken"));
    }

    public static Api admin(MockMvc mvc) throws Exception {
        return login(mvc, AbstractIntegrationTest.ADMIN_USERNAME, AbstractIntegrationTest.ADMIN_PASSWORD);
    }

    public ResultActions get(String path) throws Exception {
        return call(HttpMethod.GET, path, null);
    }

    public ResultActions post(String path, String json) throws Exception {
        return call(HttpMethod.POST, path, json);
    }

    public ResultActions put(String path, String json) throws Exception {
        return call(HttpMethod.PUT, path, json);
    }

    public ResultActions patch(String path, String json) throws Exception {
        return call(HttpMethod.PATCH, path, json);
    }

    public ResultActions delete(String path) throws Exception {
        return call(HttpMethod.DELETE, path, null);
    }

    public ResultActions call(HttpMethod method, String path, String json) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path).header("Authorization", "Bearer " + token);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(builder);
    }

    /** POST и id созданного объекта из ответа. */
    public String create(String path, String json) throws Exception {
        String body = post(path, json).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    public static String read(ResultActions result, String jsonPath) throws Exception {
        return String.valueOf((Object) JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath));
    }

    /** Новый птичник с уникальным кодом — чтобы тесты не делили активную партию. */
    public String newHouse() throws Exception {
        String code = "T-" + UUID.randomUUID().toString().substring(0, 8);
        return create("/api/v1/houses", """
                {"siteId":"%s","code":"%s","name":"Тестовый птичник %s","areaM2":1000,"capacityHeads":20000}
                """.formatted(SITE_ID, code, code));
    }

    /** Пользователь с ролью и назначенными птичниками; возвращает его клиент API. */
    public Api newUser(String role, String... houseIds) throws Exception {
        String username = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String houses = houseIds.length == 0 ? "[]" : "[\"" + String.join("\",\"", houseIds) + "\"]";
        create("/api/v1/users", """
                {"username":"%s","fullName":"Тест %s","position":"Тест","role":"%s","password":"password-123","houseIds":%s}
                """.formatted(username, role, role, houses));
        return login(mvc, username, "password-123");
    }
}
