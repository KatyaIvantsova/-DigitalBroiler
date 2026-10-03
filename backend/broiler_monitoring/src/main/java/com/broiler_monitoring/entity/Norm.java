package com.broiler_monitoring.entity;

import com.broiler_monitoring.enumerated.NormMetric;
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

/**
 * Норма показателя для интервала возраста и кросса (S3-01). breedCode = null — норма для всех кроссов.
 * Версия неизменяема: правка закрывает строку (validTo) и создаёт новую с version + 1.
 */
@Entity
@Table(name = "norms")
@Getter
@Setter
@NoArgsConstructor
public class Norm {

    @Id
    private UUID id;

    @Column(length = 32)
    private String breedCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NormMetric metric;

    @Column(nullable = false)
    private int ageFromDay;

    @Column(nullable = false)
    private int ageToDay;

    private Double minValue;

    private Double targetValue;

    private Double maxValue;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(nullable = false, columnDefinition = "text")
    private String source;

    @Column(nullable = false)
    private int version = 1;

    private UUID previousId;

    @Column(nullable = false)
    private Instant validFrom;

    private Instant validTo;

    private String changedBy;

    @Column(columnDefinition = "text")
    private String changeComment;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (validFrom == null) validFrom = Instant.now();
    }

    public boolean covers(int ageDay) {
        return ageDay >= ageFromDay && ageDay <= ageToDay;
    }
}
