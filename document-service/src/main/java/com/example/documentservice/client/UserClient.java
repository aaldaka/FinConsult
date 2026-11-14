package com.example.documentservice.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserClient {

    UserInfo getById(Long userId);

    // email and display name for many users in one call; ids the user service does not know are left out
    Map<Long, UserSummary> lookup(Collection<Long> userIds);

    List<AdminInfo> admins();
}
