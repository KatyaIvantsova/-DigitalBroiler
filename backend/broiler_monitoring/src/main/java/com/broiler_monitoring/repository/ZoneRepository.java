package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    java.util.List<Zone> findByHouseIdOrderByCodeAsc(UUID houseId);

    boolean existsByHouseIdAndCodeIgnoreCase(UUID houseId, String code);
}
