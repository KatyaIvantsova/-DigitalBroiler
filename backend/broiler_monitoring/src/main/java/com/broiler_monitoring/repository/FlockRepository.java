package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.Flock;
import com.broiler_monitoring.enumerated.FlockStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlockRepository extends JpaRepository<Flock, UUID> {

    List<Flock> findAllByOrderByPlacedAtDesc();

    List<Flock> findByStatus(FlockStatus status);

    Optional<Flock> findFirstByHouseIdAndStatus(UUID houseId, FlockStatus status);

    boolean existsByCodeIgnoreCase(String code);
}
