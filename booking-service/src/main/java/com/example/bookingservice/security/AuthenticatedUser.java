package com.example.bookingservice.security;

public record AuthenticatedUser(Long userId, String role) {}
