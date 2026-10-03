package com.broiler_monitoring.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, BootstrapAdmin bootstrapAdmin) {

    public record Jwt(String secret, Duration ttl) {
    }

    /** Первый администратор: создаётся при старте, если пользователя с таким логином нет. */
    public record BootstrapAdmin(String username, String password) {
    }
}
