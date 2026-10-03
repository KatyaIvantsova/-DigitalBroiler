package com.broiler_monitoring.security;

import com.broiler_monitoring.entity.AppUser;
import com.broiler_monitoring.enumerated.UserRole;
import com.broiler_monitoring.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Создаёт первого администратора из AUTH_BOOTSTRAP_ADMIN_USERNAME / AUTH_BOOTSTRAP_ADMIN_PASSWORD,
 * если пользователя с таким логином ещё нет. Существующего пользователя не трогает.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AuthProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(AuthProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || isBlank(admin.username()) || isBlank(admin.password())) {
            return;
        }
        if (users.findByUsernameIgnoreCase(admin.username()).isPresent()) {
            return;
        }

        AppUser user = new AppUser(UUID.randomUUID(), "Администратор", "Администратор системы");
        user.setUsername(admin.username());
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setAccessRole(UserRole.ADMIN);
        users.save(user);
        log.info("Bootstrap administrator '{}' created", admin.username());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
