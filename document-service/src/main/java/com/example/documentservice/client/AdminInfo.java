package com.example.documentservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// one entry of the user service's /internal/users/admins answer
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminInfo(Long id, String email, String displayName) {}
