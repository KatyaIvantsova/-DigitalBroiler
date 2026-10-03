package com.broiler_monitoring.repository;

import com.broiler_monitoring.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRepository extends JpaRepository<Rule, String> {

    List<Rule> findByEnabledTrue();
}
