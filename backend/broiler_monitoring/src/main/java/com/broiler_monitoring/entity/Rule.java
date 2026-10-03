package com.broiler_monitoring.entity;

import com.broiler_monitoring.Telemetry.SensorType;
import com.broiler_monitoring.enumerated.IncidentType;
import com.broiler_monitoring.enumerated.NormMetric;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Правило движка (docs/sprint2/08-rules-engine-spec.md, п. 3). */
@Entity
@Table(name = "rules")
@Getter
@Setter
@NoArgsConstructor
public class Rule {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NormMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SensorType sensorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private IncidentType incidentType;

    @Column(nullable = false)
    private int warnMinutes;

    private Double criticalDelta;

    @Column(nullable = false)
    private int criticalMinutes;

    @Column(nullable = false)
    private int clearMinutes;

    @Column(nullable = false)
    private boolean enabled = true;
}
