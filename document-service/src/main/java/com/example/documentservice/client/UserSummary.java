package com.example.documentservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// one entry of the user service's /internal/users/lookup answer
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSummary(String email, String displayName) {}
