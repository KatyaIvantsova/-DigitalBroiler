package com.broiler_monitoring.service;

import com.broiler_monitoring.dto.admin.PasswordResetRequest;
import com.broiler_monitoring.dto.admin.UserRequest;
import com.broiler_monitoring.dto.admin.UserResponse;
import com.broiler_monitoring.entity.AppUser;
import com.broiler_monitoring.enumerated.UserRole;
import com.broiler_monitoring.repository.AppUserRepository;
import com.broiler_monitoring.repository.HouseRepository;
import com.broiler_monitoring.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Управление пользователями (S2-05): создание, роль, блокировка, пароль, назначение на птичники. */
@Service
public class UserAdminService {

    private final AppUserRepository users;
    private final HouseRepository houses;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserAdminService(AppUserRepository users, HouseRepository houses, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.houses = houses;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return users.findAllByOrderByFullNameAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Задайте пароль");
        }
        requireFreeUsername(request.username(), null);
        AppUser user = new AppUser(UUID.randomUUID(), request.fullName().trim(), request.position().trim());
        user.setUsername(request.username().trim());
        user.setAccessRole(request.role());
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setHouseIds(validHouses(request.houseIds()));
        user = users.save(user);
        audit.record(AuditService.USER, user.getId(), "CREATED",
                "Создан пользователь %s (%s)".formatted(user.getUsername(), user.getAccessRole()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        AppUser user = get(id);
        requireFreeUsername(request.username(), id);
        boolean enabled = request.enabled() == null || request.enabled();
        if (id.equals(CurrentActor.get().id()) && (!enabled || request.role() != UserRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нельзя снять с себя роль администратора или заблокировать себя");
        }
        if (user.getAccessRole() == UserRole.ADMIN && user.isEnabled()
                && (request.role() != UserRole.ADMIN || !enabled)
                && users.countByAccessRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В системе должен остаться хотя бы один администратор");
        }
        Set<UUID> houseIds = validHouses(request.houseIds());
        AuditService.Changes changes = AuditService.changes()
                .field("Логин", user.getUsername(), request.username().trim())
                .field("ФИО", user.getFullName(), request.fullName().trim())
                .field("Должность", user.getRole(), request.position().trim())
                .field("Роль", user.getAccessRole(), request.role())
                .field("Активен", user.isEnabled(), enabled)
                .field("Птичники", user.getHouseIds().size(), houseIds.size());
        user.setUsername(request.username().trim());
        user.setFullName(request.fullName().trim());
        user.setRole(request.position().trim());
        user.setAccessRole(request.role());
        user.setEnabled(enabled);
        user.setHouseIds(houseIds);
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            changes.value("Пароль", "изменён");
        }
        user = users.save(user);
        audit.record(AuditService.USER, id, "UPDATED", "Изменён пользователь %s".formatted(user.getUsername()), changes);
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(UUID id, PasswordResetRequest request) {
        AppUser user = get(id);
        if (user.getUsername() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала задайте пользователю логин");
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        users.save(user);
        audit.record(AuditService.USER, id, "PASSWORD_RESET", "Сброшен пароль пользователя %s".formatted(user.getUsername()));
    }

    private AppUser get(UUID id) {
        return users.findById(id).orElseThrow(() -> StructureService.notFound("Пользователь", id));
    }

    private void requireFreeUsername(String username, UUID ownId) {
        users.findByUsernameIgnoreCase(username.trim())
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин '%s' уже занят".formatted(username));
                });
    }

    private Set<UUID> validHouses(Set<UUID> houseIds) {
        if (houseIds == null) {
            return new HashSet<>();
        }
        for (UUID houseId : houseIds) {
            if (!houses.existsById(houseId)) {
                throw StructureService.notFound("Птичник", houseId);
            }
        }
        return new HashSet<>(houseIds);
    }
}
