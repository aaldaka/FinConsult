package com.example.bookingservice.service;

import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.UserInfo;
import com.example.bookingservice.exception.AccountNotActive;
import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.ServiceUnavailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// no Spring and no database: the user service client is a mock and the clock is moved by hand
@ExtendWith(MockitoExtension.class)
class AccountStatusServiceTest {

    // a clock the test can move forward
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Mock UserClient userClient;

    private MovableClock clock;
    private AccountStatusService accountStatusService;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        accountStatusService = new AccountStatusService(userClient, clock);
    }

    private UserInfo user(long id, String status, boolean blacklisted) {
        return new UserInfo(id, "u" + id + "@test.com", "CLIENT", status, blacklisted, blacklisted ? 3 : 0, "Test User");
    }

    @Test
    void anActiveUserThatIsNotBlacklistedPasses() {
        when(userClient.getById(1L)).thenReturn(user(1, "ACTIVE", false));

        assertDoesNotThrow(() -> accountStatusService.requireActive(1L));
    }

    @Test
    void aBlacklistedUserIsRefusedEvenWithAnActiveStatus() {
        when(userClient.getById(1L)).thenReturn(user(1, "ACTIVE", true));

        AccountNotActive error = assertThrows(AccountNotActive.class, () -> accountStatusService.requireActive(1L));

        assertEquals("Your account is blacklisted", error.getMessage());
    }

    @Test
    void aUserThatIsNotActiveIsRefused() {
        when(userClient.getById(1L)).thenReturn(user(1, "INACTIVE", false));
        when(userClient.getById(2L)).thenReturn(user(2, "UNVERIFIED", false));

        assertEquals("Your account is not active",
                assertThrows(AccountNotActive.class, () -> accountStatusService.requireActive(1L)).getMessage());
        assertThrows(AccountNotActive.class, () -> accountStatusService.requireActive(2L));
    }

    @Test
    void aBurstOfCallsWithinTheCacheTimeAsksTheUserServiceOnce() {
        when(userClient.getById(1L)).thenReturn(user(1, "ACTIVE", false));

        for (int i = 0; i < 50; i++) {
            accountStatusService.requireActive(1L);
            clock.advance(Duration.ofMillis(100));
        }

        verify(userClient, times(1)).getById(1L);
    }

    @Test
    void afterTheCacheTimeTheUserServiceIsAskedAgain() {
        when(userClient.getById(1L)).thenReturn(user(1, "ACTIVE", false));
        accountStatusService.requireActive(1L);

        clock.advance(AccountStatusService.CACHE_TTL.minusSeconds(1));
        accountStatusService.requireActive(1L);
        verify(userClient, times(1)).getById(1L); // still cached

        clock.advance(Duration.ofSeconds(2));
        accountStatusService.requireActive(1L);
        verify(userClient, times(2)).getById(1L); // expired, asked again
    }

    @Test
    void aUserBlacklistedLaterIsBlockedOnceTheCacheExpires() {
        when(userClient.getById(1L)).thenReturn(user(1, "ACTIVE", false), user(1, "ACTIVE", true));
        accountStatusService.requireActive(1L);

        clock.advance(AccountStatusService.CACHE_TTL.plusSeconds(1));

        assertThrows(AccountNotActive.class, () -> accountStatusService.requireActive(1L));
    }

    @Test
    void theCacheTimeIsBetweenThirtyAndSixtySeconds() {
        long seconds = AccountStatusService.CACHE_TTL.toSeconds();
        assertEquals(true, seconds >= 30 && seconds <= 60, "was " + seconds);
    }

    @Test
    void eachUserIsCachedOnTheirOwn() {
        when(userClient.getById(1L)).thenReturn(user(1, "ACTIVE", false));
        when(userClient.getById(2L)).thenReturn(user(2, "ACTIVE", true));

        accountStatusService.requireActive(1L);
        assertThrows(AccountNotActive.class, () -> accountStatusService.requireActive(2L));
        accountStatusService.requireActive(1L);
        assertThrows(AccountNotActive.class, () -> accountStatusService.requireActive(2L));

        verify(userClient, times(1)).getById(1L);
        verify(userClient, times(1)).getById(2L);
    }

    @Test
    void aFailureIsNotCachedAndIsNotAnsweredFromAnOldAnswer() {
        when(userClient.getById(1L))
                .thenThrow(new ServiceUnavailable("The user service is not available, please try again later"))
                .thenReturn(user(1, "ACTIVE", false));

        assertThrows(ServiceUnavailable.class, () -> accountStatusService.requireActive(1L)); // down: the action is refused
        assertDoesNotThrow(() -> accountStatusService.requireActive(1L));                      // back up: asked again at once
        verify(userClient, times(2)).getById(1L);
    }

    @Test
    void aStaleAnswerIsNotUsedWhenTheUserServiceIsDownAfterTheCacheExpires() {
        when(userClient.getById(1L))
                .thenReturn(user(1, "ACTIVE", false))
                .thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));
        accountStatusService.requireActive(1L);

        clock.advance(AccountStatusService.CACHE_TTL.plusSeconds(1));

        assertThrows(ServiceUnavailable.class, () -> accountStatusService.requireActive(1L));
    }

    @Test
    void anUnknownUserIsNotFoundAndNotCached() {
        when(userClient.getById(9L)).thenThrow(new InformationNotFound("User not found"));

        assertThrows(InformationNotFound.class, () -> accountStatusService.requireActive(9L));
        assertThrows(InformationNotFound.class, () -> accountStatusService.requireActive(9L));

        verify(userClient, times(2)).getById(9L);
        verifyNoMoreInteractions(userClient);
    }
}
