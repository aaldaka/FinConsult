package com.example.bookingservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// the document service's /internal/kyc/approved answer
@JsonIgnoreProperties(ignoreUnknown = true)
public record KycApproval(Boolean approved) {}
