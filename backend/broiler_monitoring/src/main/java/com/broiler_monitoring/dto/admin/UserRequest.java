package com.broiler_monitoring.dto.admin;

import com.broiler_monitoring.enumerated.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Создание и правка пользователя администратором. password обязателен только при создании. */
public record UserRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "латиница, цифры, точка, дефис, подчёркивание")
        String username,
        @NotBlank String fullName,
        @NotBlank String position,
        @NotNull UserRole role,
        Boolean enabled,
        @Size(min = 8, max = 128, message = "не короче 8 символов") String password,
        Set<UUID> houseIds
) {
}
