package com.example.bookingservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StrikeResult(int noShowStrikes, boolean blacklisted) {}
