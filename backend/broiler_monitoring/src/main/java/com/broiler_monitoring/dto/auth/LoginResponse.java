package com.broiler_monitoring.dto.auth;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, CurrentUserResponse user) {
}
