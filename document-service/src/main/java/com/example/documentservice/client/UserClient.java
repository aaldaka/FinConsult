package com.example.documentservice.client;

import com.example.documentservice.model.enums.Specialty;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserClient {

    UserInfo getById(Long userId);

    // email and display name for many users in one call; ids the user service does not know are left out
    Map<Long, UserSummary> lookup(Collection<Long> userIds);

    List<AdminInfo> admins();

    // creates the advisor profile and makes the user an advisor; NotEligible (a 409 from the user service) if it refuses
    PromotionResult promote(Long userId, String title, Specialty specialty, int yearsOfExperience);

    // the user's advisor profile, InformationNotFound if they have none
    AdvisorProfile getAdvisorByUser(Long userId);
}
