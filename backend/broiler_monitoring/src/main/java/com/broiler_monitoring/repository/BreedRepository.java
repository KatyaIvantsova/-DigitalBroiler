package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.Breed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreedRepository extends JpaRepository<Breed, String> {
}
