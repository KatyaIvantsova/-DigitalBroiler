package com.broiler_monitoring.entity;

import com.broiler_monitoring.enumerated.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    /** Должность для отображения, например «Директор по качеству». */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(unique = true, length = 64)
    private String username;

    @JsonIgnore
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_role", nullable = false, length = 32)
    private UserRole accessRole = UserRole.OPERATOR;

    @Column(nullable = false)
    private boolean enabled = true;

    public AppUser(UUID id, String fullName, String role) {
        this.id = id;
        this.fullName = fullName;
        this.role = role;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
