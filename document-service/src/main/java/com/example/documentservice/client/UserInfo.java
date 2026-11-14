package com.example.documentservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// the fields of the user service's InternalUserResponse, status and role as the names it sends
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInfo(Long id, String email, String role, String status, boolean blacklisted, int noShowStrikes, String displayName) {}
