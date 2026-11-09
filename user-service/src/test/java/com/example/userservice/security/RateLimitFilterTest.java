package com.example.userservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// no Spring and no database: the filter is called directly
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    private record Call(MockHttpServletResponse response, boolean reachedTheController) {}

    private Call call(String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Call(response, chain.getRequest() != null);
    }

    @Test
    void theFirstFiveRequestsPassAndTheSixthIsA429WithRetryAfter() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Call ok = call("/auth/login", "10.0.0.1");
            assertTrue(ok.reachedTheController(), "request " + i + " should pass");
            assertEquals(200, ok.response().getStatus());
        }

        Call blocked = call("/auth/login", "10.0.0.1");

        assertFalse(blocked.reachedTheController());
        assertEquals(429, blocked.response().getStatus());
        long retryAfter = Long.parseLong(blocked.response().getHeader("Retry-After"));
        assertTrue(retryAfter > 0 && retryAfter <= 60, "retry after was " + retryAfter);
        assertEquals("application/json", blocked.response().getContentType());
        assertTrue(blocked.response().getContentAsString().contains("Too many requests"));
    }

    @Test
    void eachIpHasItsOwnBucket() throws Exception {
        for (int i = 0; i < 6; i++) {
            call("/auth/login", "10.0.0.2");
        }

        assertEquals(429, call("/auth/login", "10.0.0.2").response().getStatus());
        assertTrue(call("/auth/login", "10.0.0.3").reachedTheController());
    }

    @Test
    void theFourLimitedEndpointsShareOneBucketPerIp() throws Exception {
        assertTrue(call("/auth/login", "10.0.0.4").reachedTheController());
        assertTrue(call("/auth/register", "10.0.0.4").reachedTheController());
        assertTrue(call("/auth/forget-password", "10.0.0.4").reachedTheController());
        assertTrue(call("/auth/reset-password", "10.0.0.4").reachedTheController());
        assertTrue(call("/auth/login", "10.0.0.4").reachedTheController());

        assertEquals(429, call("/auth/register", "10.0.0.4").response().getStatus());
    }

    @Test
    void otherEndpointsAreNeverLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertTrue(call("/auth/change-password", "10.0.0.5").reachedTheController());
            assertTrue(call("/auth/verify-email", "10.0.0.5").reachedTheController());
            assertTrue(call("/actuator/health", "10.0.0.5").reachedTheController());
        }
    }

    @Test
    void aBlockedRequestDoesNotCountAgainstAnotherEndpoint() throws Exception {
        for (int i = 0; i < 6; i++) {
            call("/auth/login", "10.0.0.6");
        }

        // the unlimited endpoints still work for a blocked ip
        Call stillOpen = call("/auth/verify-email", "10.0.0.6");
        assertTrue(stillOpen.reachedTheController());
        assertEquals(200, stillOpen.response().getStatus());
        assertNull(stillOpen.response().getHeader("Retry-After"));
        assertNotNull(stillOpen.response());
    }
}
