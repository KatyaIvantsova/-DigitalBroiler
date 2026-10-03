package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.Norm;
import com.broiler_monitoring.enumerated.NormMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NormRepository extends JpaRepository<Norm, UUID> {

    List<Norm> findByValidToIsNullOrderByMetricAscBreedCodeAscAgeFromDayAsc();

    List<Norm> findByMetricAndValidToIsNull(NormMetric metric);

    Optional<Norm> findByPreviousId(UUID previousId);

    boolean existsByValidToIsNull();
}
