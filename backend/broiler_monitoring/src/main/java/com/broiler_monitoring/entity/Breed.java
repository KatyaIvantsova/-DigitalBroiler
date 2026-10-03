package com.broiler_monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Кросс (порода): ROSS_308, COBB_500. */
@Entity
@Table(name = "breeds")
@Getter
@Setter
@NoArgsConstructor
public class Breed {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false)
    private String name;
}
