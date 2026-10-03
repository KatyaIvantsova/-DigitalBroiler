package com.broiler_monitoring.dto.admin;

import com.broiler_monitoring.entity.AppUser;
import com.broiler_monitoring.enumerated.UserRole;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String fullName,
        String position,
        UserRole role,
        boolean enabled,
        boolean canLogin,
        Set<UUID> houseIds,
        LocalDateTime createdAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.getAccessRole(), user.isEnabled(), user.getUsername() != null && user.getPasswordHash() != null,
                Set.copyOf(user.getHouseIds()), user.getCreatedAt());
    }
}
