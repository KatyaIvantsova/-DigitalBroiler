package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    boolean existsByCodeIgnoreCase(String code);
}
