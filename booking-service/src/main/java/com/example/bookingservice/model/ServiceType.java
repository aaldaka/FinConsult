package com.example.bookingservice.model;

import com.example.bookingservice.model.enums.DocumentType;
import com.example.bookingservice.model.enums.Specialty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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
