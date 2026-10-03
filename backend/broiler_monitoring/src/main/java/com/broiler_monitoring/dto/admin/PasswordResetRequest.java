package com.broiler_monitoring.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(@NotBlank @Size(min = 8, max = 128, message = "не короче 8 символов") String password) {
}
