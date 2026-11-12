package com.example.bookingservice.client;

import java.util.Collection;
import java.util.Map;

public interface UserClient {

    Map<Long, String> names(Collection<Long> userIds);
}
