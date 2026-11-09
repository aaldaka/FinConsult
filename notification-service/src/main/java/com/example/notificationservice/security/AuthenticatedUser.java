package com.example.notificationservice.security;

/** The caller, read straight from the JWT claims. There is no users table in this service. */
public record AuthenticatedUser(Long userId, String role) {}
