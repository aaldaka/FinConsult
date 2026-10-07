package com.example.finConsult.model;

import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.Specialty;
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

    @Enumerated(EnumType.STRING) //added for KYC type checks before
    @Column(name = "required_document_type")
    private DocumentType requiredDocumentType;   // null = any approved document

    @Enumerated(EnumType.STRING)
    @Column(name = "required_specialty")
    private Specialty requiredSpecialty;   // null = any advisor can offer it
}