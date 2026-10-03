package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.House;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HouseRepository extends JpaRepository<House, UUID> {

    java.util.List<House> findAllByOrderByCodeAsc();

    boolean existsBySiteIdAndCodeIgnoreCase(UUID siteId, String code);
}
