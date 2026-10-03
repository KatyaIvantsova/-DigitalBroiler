package com.broiler_monitoring.security;

import com.broiler_monitoring.enumerated.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Кто выполняет запрос: id и имя из JWT, роль из authorities.
 * Для запросов без пользователя (планировщик, ключ телеметрии) — SYSTEM с id = null.
 */
public record CurrentActor(UUID id, String name, UserRole role) {

    public static final CurrentActor SYSTEM = new CurrentActor(null, "Система", null);

    public static CurrentActor get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM;
        }
        UserRole role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> parseRole(authority.substring("ROLE_".length())))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (role == null) {
            return SYSTEM;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String name = jwt.getClaimAsString("name");
            return new CurrentActor(parseUuid(jwt.getSubject()), name != null ? name : jwt.getSubject(), role);
        }
        return new CurrentActor(null, authentication.getName(), role);
    }

    public boolean hasAnyRole(UserRole... roles) {
        for (UserRole candidate : roles) {
            if (candidate == role) return true;
        }
        return false;
    }

    private static UserRole parseRole(String value) {
        try {
            return UserRole.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
