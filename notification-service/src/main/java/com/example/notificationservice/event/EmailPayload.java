package com.example.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

// the payload of an EMAIL event, see docs/events.md
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailPayload(String template, String to, Map<String, Object> data) {
}
