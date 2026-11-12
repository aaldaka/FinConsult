package com.example.bookingservice.client;

import java.util.Collection;
import java.util.Map;

public interface UserClient {

    UserInfo getById(Long userId);

    Map<Long, String> names(Collection<Long> userIds);
}
