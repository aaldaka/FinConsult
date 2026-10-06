package com.example.finConsult.event;

import com.example.finConsult.model.response.NotificationResponse;


public record NotificationCreatedEvent(Long userId, NotificationResponse payload) {}

