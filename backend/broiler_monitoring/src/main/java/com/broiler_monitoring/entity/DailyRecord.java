package com.broiler_monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Ежедневный учёт по партии: падёж, выбраковка, корм и вода за сутки. История правок — в audit_log. */
@Entity
@Table(name = "daily_records")
@Getter
@Setter
@NoArgsConstructor
public class DailyRecord {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID flockId;

    @Column(nullable = false)
    private LocalDate recordDate;

    @Column(nullable = false)
    private int mortalityHeads;

    @Column(nullable = false)
    private int culledHeads;

    private Double feedConsumedKg;

    @Column(name = "water_consumed_l")
    private Double waterConsumedL;

    @Column(columnDefinition = "text")
    private String comment;

    private UUID createdBy;

    private UUID updatedBy;

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
