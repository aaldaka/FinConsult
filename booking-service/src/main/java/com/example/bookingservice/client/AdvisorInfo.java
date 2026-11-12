package com.example.bookingservice.client;

import com.example.bookingservice.model.enums.Specialty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdvisorInfo(Long id, Long userId, boolean active, String displayName, String title, Specialty specialty) {}
