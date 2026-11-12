package com.example.bookingservice.client;

import java.util.Collection;
import java.util.Map;

public interface AdvisorClient {

    AdvisorInfo getById(Long advisorId);

    AdvisorInfo getByUserId(Long userId);

    Map<Long, AdvisorInfo> lookup(Collection<Long> advisorIds);
}
