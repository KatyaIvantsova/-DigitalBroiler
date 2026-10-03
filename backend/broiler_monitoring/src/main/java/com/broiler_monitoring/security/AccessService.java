package com.broiler_monitoring.security;

import com.broiler_monitoring.entity.AppUser;
import com.broiler_monitoring.enumerated.UserRole;
import com.broiler_monitoring.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

/**
 * Права на уровне данных (S2-04, S2-05): оператор видит и правит только птичники,
 * на которые его назначили. Остальные роли видят все птичники площадки.
 */
@Service
public class AccessService {

    private final AppUserRepository users;

    public AccessService(AppUserRepository users) {
        this.users = users;
    }

    /** null — ограничений нет (все птичники). */
    public Set<UUID> visibleHouseIds() {
        CurrentActor actor = CurrentActor.get();
        if (actor.role() != UserRole.OPERATOR) {
            return null;
        }
        if (actor.id() == null) {
            return Set.of();
        }
        return users.findById(actor.id()).map(AppUser::getHouseIds).map(Set::copyOf).orElse(Set.of());
    }

    public boolean canSeeHouse(UUID houseId) {
        Set<UUID> visible = visibleHouseIds();
        return visible == null || visible.contains(houseId);
    }

    public void requireHouse(UUID houseId) {
        if (!canSeeHouse(houseId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому птичнику");
        }
    }

    public void requireRole(String message, UserRole... roles) {
        if (!CurrentActor.get().hasAnyRole(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }
}
