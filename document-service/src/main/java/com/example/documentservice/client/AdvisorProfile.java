package com.example.documentservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// the fields of the user service's /internal/advisors/by-user answer that this service uses
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdvisorProfile(Long id, Long userId) {}
