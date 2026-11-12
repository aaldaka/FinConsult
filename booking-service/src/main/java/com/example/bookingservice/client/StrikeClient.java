package com.example.bookingservice.client;

public interface StrikeClient {

    StrikeResult addStrike(Long userId);

    StrikeResult removeStrike(Long userId);
}
