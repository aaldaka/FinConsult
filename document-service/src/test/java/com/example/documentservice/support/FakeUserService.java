package com.example.documentservice.support;

import com.example.documentservice.client.AdvisorProfile;
import com.example.documentservice.client.PromotionResult;
import com.example.documentservice.client.UserClient;
import com.example.documentservice.client.UserInfo;
import com.example.documentservice.client.UserSummary;
import com.example.documentservice.exception.InformationNotFound;
import com.example.documentservice.exception.NotEligible;
import com.example.documentservice.model.enums.Specialty;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

// plays the user service behind a mocked UserClient: everyone is an active client until promoted, a promotion happens
// once (a second one gets the same 409 the real service sends), and after it the user is an advisor with a profile.
// beforeEachCall runs first in every answer, so a test can see which calls happen inside a transaction
public class FakeUserService {

    private final Set<Long> promoted = ConcurrentHashMap.newKeySet();
    private final Set<Long> blacklisted = ConcurrentHashMap.newKeySet();
    private final Set<Long> inactive = ConcurrentHashMap.newKeySet();
    private final AtomicLong promotions = new AtomicLong();

    public FakeUserService(UserClient userClient, Runnable beforeEachCall) {
        lenient().when(userClient.getById(anyLong())).thenAnswer(invocation -> {
            beforeEachCall.run();
            Long id = invocation.getArgument(0);
            return new UserInfo(id, email(id), promoted.contains(id) ? "ADVISOR" : "CLIENT",
                    inactive.contains(id) ? "INACTIVE" : "ACTIVE", blacklisted.contains(id), 0, name(id));
        });
        lenient().when(userClient.lookup(any())).thenAnswer(invocation -> {
            beforeEachCall.run();
            Map<Long, UserSummary> users = new HashMap<>();
            for (Long id : invocation.<Collection<Long>>getArgument(0)) {
                users.put(id, new UserSummary(email(id), name(id)));
            }
            return users;
        });
        lenient().when(userClient.promote(anyLong(), anyString(), any(Specialty.class), anyInt())).thenAnswer(invocation -> {
            beforeEachCall.run();
            Long id = invocation.getArgument(0);
            if (!promoted.add(id)) {
                throw new NotEligible("This user is already an advisor");
            }
            promotions.incrementAndGet();
            return new PromotionResult(id + 100_000, id);
        });
        lenient().when(userClient.getAdvisorByUser(anyLong())).thenAnswer(invocation -> {
            beforeEachCall.run();
            Long id = invocation.getArgument(0);
            if (!promoted.contains(id)) {
                throw new InformationNotFound("Advisor not found");
            }
            return new AdvisorProfile(id + 100_000, id);
        });
    }

    public static String email(long id) {
        return "user" + id + "@test.com";
    }

    public static String name(long id) {
        return "Person " + id;
    }

    public void blacklist(long id) {
        blacklisted.add(id);
    }

    public void deactivate(long id) {
        inactive.add(id);
    }

    public void promoteOutsideTheService(long id) {
        promoted.add(id);
    }

    public boolean isPromoted(long id) {
        return promoted.contains(id);
    }

    public long promotions() {
        return promotions.get();
    }
}
