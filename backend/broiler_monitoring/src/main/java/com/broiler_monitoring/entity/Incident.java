package com.broiler_monitoring.entity;


import com.broiler_monitoring.enumerated.IncidentPriority;
import com.broiler_monitoring.enumerated.IncidentSource;
import com.broiler_monitoring.enumerated.IncidentStatus;
import com.broiler_monitoring.enumerated.IncidentType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false,unique = true)
    private String code;

    @NotBlank
    @Column (nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentPriority priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSource source;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false)
    private IncidentType type;

    private String workshop;

    private String house;

    private String zone;

    private UUID notificationId;

    private UUID houseId;

    private UUID zoneId;

    private UUID flockId;

    private UUID sensorId;

    /** Код правила движка, создавшего инцидент (S3-03); null для ручных инцидентов. */
    @Column(length = 64)
    private String ruleCode;

    private UUID assigneeId;

    private String assigneeRole;

    private String responsible;

    @Column(columnDefinition = "text")
    private String decisionComment;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime detectedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime startedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime resolvedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime closedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long reactionMinutes;

    @PrePersist
    public void prePersist(){
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status==null){
            status = IncidentStatus.OPEN;
        }
        if (source==null){
            source = IncidentSource.MANUAL;
        }
        if (priority==null){
            priority = IncidentPriority.MEDIUM;
        }
        if (type==null){
            type = IncidentType.OTHER;
        }
        if (detectedAt==null)
            detectedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate(){
        updatedAt = LocalDateTime.now();
    }







}
