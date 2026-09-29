package com.example.finConsult.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "service_types")
public class ServiceType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

    // columdef = "TEXT" incase longer than 255 chars needed
    @Column
    private String description;

    @Column
    private int durationMinutes;

    @Column
    private boolean requiresKyc = false;

    private boolean active = true;
}