package com.broiler_monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Птичник (корпус) на площадке. */
@Entity
@Table(name = "houses")
@Getter
@Setter
@NoArgsConstructor
public class House {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID siteId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "area_m2")
    private Double areaM2;

    private Integer capacityHeads;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
