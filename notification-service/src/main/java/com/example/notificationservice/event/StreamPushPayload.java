package com.example.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

// payload of a STREAM_PUSH event: a signal for the user's open browser only, nothing is stored
@JsonIgnoreProperties(ignoreUnknown = true)
public record StreamPushPayload(Long userId, String eventName, Map<String, Object> data) {}
