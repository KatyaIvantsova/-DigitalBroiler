package com.broiler_monitoring.security;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * S2-04: матрица прав на уровне URL. Для каждой строки: метод, путь и роли, которым запрос разрешён.
 * Остальные роли должны получить 403. Тело запроса пустое, поэтому разрешённые роли получают 400/404 — главное, не 403.
 * Таблица совпадает с docs/sprint2/04-roles-matrix.md.
 */
class RoleMatrixIntegrationTest extends AbstractIntegrationTest {

    private static final String ALL = "OPERATOR TECHNOLOGIST VETERINARIAN MANAGER ADMIN";
    private static final String ID = "00000000-0000-0000-0000-00000000ffff";

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource(delimiter = '|', textBlock = """
            GET    | /api/v1/flocks                                   | OPERATOR TECHNOLOGIST VETERINARIAN MANAGER ADMIN
            POST   | /api/v1/flocks                                   | TECHNOLOGIST ADMIN
            PUT    | /api/v1/flocks/%1$s                              | TECHNOLOGIST ADMIN
            POST   | /api/v1/flocks/%1$s/close                        | TECHNOLOGIST ADMIN
            POST   | /api/v1/flocks/%1$s/daily-records                | OPERATOR TECHNOLOGIST VETERINARIAN ADMIN
            PUT    | /api/v1/flocks/%1$s/daily-records/%1$s           | OPERATOR TECHNOLOGIST VETERINARIAN ADMIN
            POST   | /api/v1/flocks/%1$s/weighings                    | OPERATOR TECHNOLOGIST VETERINARIAN ADMIN
            GET    | /api/v1/houses                                   | OPERATOR TECHNOLOGIST VETERINARIAN MANAGER ADMIN
            POST   | /api/v1/houses                                   | ADMIN
            PUT    | /api/v1/houses/%1$s                              | ADMIN
            POST   | /api/v1/houses/%1$s/zones                        | ADMIN
            DELETE | /api/v1/zones/%1$s                               | ADMIN
            POST   | /api/v1/sites                                    | ADMIN
            GET    | /api/v1/sensors                                  | OPERATOR TECHNOLOGIST VETERINARIAN MANAGER ADMIN
            POST   | /api/v1/sensors                                  | ADMIN
            PATCH  | /api/v1/sensors/%1$s/location                    | ADMIN
            GET    | /api/v1/users                                    | ADMIN
            POST   | /api/v1/users                                    | ADMIN
            GET    | /api/v1/audit                                    | TECHNOLOGIST MANAGER ADMIN
            POST   | /api/v1/incidents                                | OPERATOR TECHNOLOGIST VETERINARIAN MANAGER ADMIN
            PATCH  | /api/v1/incidents/%1$s/status                    | OPERATOR TECHNOLOGIST VETERINARIAN MANAGER ADMIN
            POST   | /api/v1/telemetry/readings                       | ADMIN
            """)
    void matrix(String method, String path, String allowedRoles) throws Exception {
        Set<String> allowed = Arrays.stream(allowedRoles.trim().split("\\s+")).collect(Collectors.toSet());
        for (String role : ALL.split(" ")) {
            int status = mvc.perform(request(HttpMethod.valueOf(method.trim()), path.trim().formatted(ID))
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andReturn().getResponse().getStatus();
            if (allowed.contains(role)) {
                assertThat(status).as("%s %s для %s", method, path, role).isNotEqualTo(403);
            } else {
                assertThat(status).as("%s %s для %s", method, path, role).isEqualTo(403);
            }
        }
    }
}
