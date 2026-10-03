package com.broiler_monitoring.dto.auth;

import com.broiler_monitoring.entity.AppUser;
import com.broiler_monitoring.enumerated.UserRole;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String username, String fullName, String position, UserRole role) {

    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(
                user.getId(), user.getUsername(), user.getFullName(), user.getRole(), user.getAccessRole());
    }
}
