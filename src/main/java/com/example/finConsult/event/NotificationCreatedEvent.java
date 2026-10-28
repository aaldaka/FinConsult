package com.example.finConsult.event;

import com.example.finConsult.model.response.NotificationResponse;


/** Published when a notification is saved, so it can be pushed over SSE after the commit. */
public record NotificationCreatedEvent(Long userId, NotificationResponse payload) {}

