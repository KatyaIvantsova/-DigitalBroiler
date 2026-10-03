package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.Weighing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WeighingRepository extends JpaRepository<Weighing, UUID> {

    List<Weighing> findByFlockIdOrderByWeighedAtAsc(UUID flockId);
}
