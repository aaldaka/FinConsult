package com.example.documentservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// the user service's /internal/advisors/promote answer
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromotionResult(Long advisorId, Long userId) {}
