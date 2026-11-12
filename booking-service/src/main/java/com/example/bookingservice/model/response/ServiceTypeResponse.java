package com.example.bookingservice.model.response;

import com.example.bookingservice.model.enums.DocumentType;
import com.example.bookingservice.model.enums.Specialty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ServiceTypeResponse {
    private Long id;
    private String name;
    private String description;
    private int durationMinutes;
    private boolean requiresKyc;
    private DocumentType requiredDocumentType; // null means any approved document
    private Specialty requiredSpecialty; // null means any advisor can offer it
}
