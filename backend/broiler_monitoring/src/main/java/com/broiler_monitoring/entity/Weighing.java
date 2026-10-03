package com.broiler_monitoring.entity;

import com.broiler_monitoring.enumerated.WeighingMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Контрольное взвешивание выборки птиц. */
@Entity
@Table(name = "weighings")
@Getter
@Setter
@NoArgsConstructor
public class Weighing {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID flockId;

    private UUID zoneId;

    @Column(nullable = false)
    private Instant weighedAt;

    @Column(nullable = false)
    private Integer sampleHeads;

    @Column(name = "avg_weight_g", nullable = false)
    private Double avgWeightG;

    private Double uniformityPct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WeighingMethod method = WeighingMethod.MANUAL;

    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
