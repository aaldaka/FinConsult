package com.example.bookingservice.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// no Spring and no database
class InternalKeyFilterTest {

    private static final String KEY = "unit-test-internal-key";

    private final InternalKeyFilter filter = new InternalKeyFilter(KEY);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String path, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        if (key != null) {
            request.addHeader(InternalKeyFilter.HEADER, key);
        }
        return request;
    }

    private MockHttpServletResponse run(InternalKeyFilter f, MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        f.doFilter(request, response, chain);
        return response;
    }

    @Test
    void theRightKeyPassesAndGrantsTheInternalAuthority() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter, request("/internal/appointments/cancel-for-client/1", KEY), chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNotNull(chain.getRequest(), "the request went on down the chain");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(InternalKeyFilter.AUTHORITY)));
    }

    @Test
    void aWrongOrMissingKeyIsAForbiddenAndStopsTheChain() throws Exception {
        for (String given : new String[]{"wrong", KEY + "x", KEY.substring(1), "", null}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(filter, request("/internal/appointments/cancel-for-client/1", given), chain);

            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus(), "key was " + given);
            assertNull(chain.getRequest(), "the chain must not run for key " + given);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Test
    void theKeyIsCaseSensitive() throws Exception {
        MockHttpServletResponse response = run(filter, request("/internal/appointments/cancel-for-client/1", KEY.toUpperCase()), new MockFilterChain());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void withNoConfiguredKeyEverythingIsRefusedEvenAnEmptyHeader() throws Exception {
        for (String configured : new String[]{"", "   ", null}) {
            InternalKeyFilter unconfigured = new InternalKeyFilter(configured);

            assertEquals(HttpServletResponse.SC_FORBIDDEN,
                    run(unconfigured, request("/internal/appointments/cancel-for-client/1", ""), new MockFilterChain()).getStatus());
            assertEquals(HttpServletResponse.SC_FORBIDDEN,
                    run(unconfigured, request("/internal/appointments/cancel-for-client/1", null), new MockFilterChain()).getStatus());
        }
    }

    @Test
    void otherPathsAreNotTouchedSoTheKeyGrantsNothingThere() throws Exception {
        for (String path : new String[]{"/appointments/mine", "/slots", "/service-types", "/internals", "/api/internal/appointments/1"}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(filter, request(path, KEY), chain);

            assertEquals(HttpServletResponse.SC_OK, response.getStatus(), path);
            assertNotNull(chain.getRequest(), path);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), "no authority is granted on " + path);
        }
    }
}
