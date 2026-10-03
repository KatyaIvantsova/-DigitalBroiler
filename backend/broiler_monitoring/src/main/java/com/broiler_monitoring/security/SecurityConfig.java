package com.broiler_monitoring.security;

import com.broiler_monitoring.enumerated.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

@Configuration
public class SecurityConfig {

    private static final String[] USER_ROLES = Arrays.stream(UserRole.values())
            .map(Enum::name)
            .toArray(String[]::new);
    private static final String OPERATOR = UserRole.OPERATOR.name();
    private static final String TECHNOLOGIST = UserRole.TECHNOLOGIST.name();
    private static final String VETERINARIAN = UserRole.VETERINARIAN.name();
    private static final String MANAGER = UserRole.MANAGER.name();
    private static final String ADMIN = UserRole.ADMIN.name();

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${telemetry.ingest.api-key:}") String telemetryApiKey
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/telemetry/readings")
                        .hasAnyAuthority(ingestOrAuthorities(ADMIN))
                        // Матрица прав S2-04 (docs/sprint2/04-roles-matrix.md). Чтение доступно всем ролям,
                        // ограничения на запись перечислены ниже; оператора дополнительно ограничивает AccessService.
                        .requestMatchers("/api/v1/users/**").hasRole(ADMIN)
                        .requestMatchers("/api/v1/audit/**").hasAnyRole(TECHNOLOGIST, MANAGER, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole(USER_ROLES)
                        .requestMatchers("/api/v1/sites/**", "/api/v1/houses/**", "/api/v1/zones/**", "/api/v1/sensors/**")
                        .hasRole(ADMIN)
                        .requestMatchers("/api/v1/flocks/*/daily-records/**", "/api/v1/flocks/*/weighings/**")
                        .hasAnyRole(OPERATOR, TECHNOLOGIST, VETERINARIAN, ADMIN)
                        .requestMatchers("/api/v1/flocks/**").hasAnyRole(TECHNOLOGIST, ADMIN)
                        .requestMatchers("/api/v1/norms/**", "/api/v1/rules/**").hasAnyRole(TECHNOLOGIST, ADMIN)
                        .anyRequest().hasAnyRole(USER_ROLES))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterBefore(new TelemetryApiKeyFilter(telemetryApiKey), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static String[] ingestOrAuthorities(String... roles) {
        String[] result = new String[roles.length + 1];
        for (int i = 0; i < roles.length; i++) {
            result[i] = "ROLE_" + roles[i];
        }
        result[roles.length] = TelemetryApiKeyFilter.AUTHORITY;
        return result;
    }
}
