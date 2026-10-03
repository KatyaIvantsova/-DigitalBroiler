package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    java.util.List<AppUser> findAllByOrderByFullNameAsc();

    long countByAccessRoleAndEnabledTrue(com.broiler_monitoring.enumerated.UserRole role);
}
