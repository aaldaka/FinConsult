package com.example.documentservice.client;

import com.example.documentservice.exception.InformationNotFound;
import com.example.documentservice.exception.ServiceUnavailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// no Spring context, no database and no real user service: MockRestServiceServer plays the user service
class UserServiceClientTest {

    private static final String BASE = "http://user-service.test";
    private static final String KEY = "client-test-key";

    private MockRestServiceServer server;
    private RestUserClient users;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = UserServiceClientConfig.restClientBuilder(BASE, KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        users = new RestUserClient(builder.build());
    }

    @Test
    void lookupSendsTheIdsOnceWithTheKeyAndReadsEmailsAndNames() {
        server.expect(requestTo(BASE + "/internal/users/lookup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", KEY))
                .andExpect(content().json("[12, 13]"))
                .andRespond(withSuccess("{\"12\": {\"email\": \"a@test.com\", \"displayName\": \"Reem Ali\", \"new\": 1}, "
                        + "\"13\": {\"email\": \"b@test.com\", \"displayName\": \"Sara Omar\"}}", MediaType.APPLICATION_JSON));

        Map<Long, UserSummary> result = users.lookup(List.of(12L, 13L, 12L));

        assertEquals(new UserSummary("a@test.com", "Reem Ali"), result.get(12L));
        assertEquals(new UserSummary("b@test.com", "Sara Omar"), result.get(13L));
        server.verify();
    }

    @Test
    void anEmptyLookupAsksNothing() {
        assertTrue(users.lookup(List.of()).isEmpty());
        assertTrue(users.lookup(null).isEmpty());
        server.verify();
    }

    @Test
    void adminsAndASingleUserAreRead() {
        server.expect(requestTo(BASE + "/internal/users/admins"))
                .andExpect(header("X-Internal-Key", KEY))
                .andRespond(withSuccess("[{\"id\": 1, \"email\": \"admin@test.com\", \"displayName\": \"Head Admin\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/internal/users/12"))
                .andRespond(withSuccess("{\"id\": 12, \"email\": \"a@test.com\", \"role\": \"CLIENT\", \"status\": \"ACTIVE\", "
                        + "\"blacklisted\": false, \"noShowStrikes\": 0, \"displayName\": \"Reem Ali\"}", MediaType.APPLICATION_JSON));

        assertEquals(List.of(new AdminInfo(1L, "admin@test.com", "Head Admin")), users.admins());
        assertEquals("CLIENT", users.getById(12L).role());
        server.verify();
    }

    @Test
    void anUnknownUserIs404AndAnyOtherFailureIs503() {
        server.expect(requestTo(BASE + "/internal/users/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/internal/users/lookup")).andRespond(withServerError());
        server.expect(requestTo(BASE + "/internal/users/admins")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(InformationNotFound.class, () -> users.getById(99L));
        assertThrows(ServiceUnavailable.class, () -> users.lookup(List.of(1L)));
        assertThrows(ServiceUnavailable.class, () -> users.admins());
    }
}
