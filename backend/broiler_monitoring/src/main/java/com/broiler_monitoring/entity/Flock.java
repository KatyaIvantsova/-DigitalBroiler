package com.broiler_monitoring.entity;

import com.broiler_monitoring.enumerated.FlockSex;
import com.broiler_monitoring.enumerated.FlockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Партия — цикл выращивания в птичнике от посадки (день 0) до убоя. */
@Entity
@Table(name = "flocks")
@Getter
@Setter
@NoArgsConstructor
public class Flock {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID houseId;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String breedCode;

    @Column(nullable = false)
    private LocalDate placedAt;

    @Column(nullable = false)
    private Integer placedHeads;

    @Column(name = "placed_avg_weight_g")
    private Double placedAvgWeightG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlockSex sex = FlockSex.MIXED;

    private String hatchery;

    private Integer targetAgeDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlockStatus status;

    private LocalDate closedAt;

    private Integer shippedHeads;

    private Double shippedLiveWeightKg;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
