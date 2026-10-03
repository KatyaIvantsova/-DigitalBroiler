package com.broiler_monitoring.Controller;

import com.broiler_monitoring.dto.admin.PasswordResetRequest;
import com.broiler_monitoring.dto.admin.UserRequest;
import com.broiler_monitoring.dto.admin.UserResponse;
import com.broiler_monitoring.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Пользователи", description = "Администрирование пользователей, ролей и назначений на птичники")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Пользователи")
    public List<UserResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать пользователя")
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить пользователя: роль, блокировка, птичники, пароль")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Сбросить пароль")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request) {
        service.resetPassword(id, request);
    }
}
