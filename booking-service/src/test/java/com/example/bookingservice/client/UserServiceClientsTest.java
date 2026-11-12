package com.example.bookingservice.client;

import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.model.enums.Specialty;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// no Spring context, no database and no real user service: MockRestServiceServer plays the user service,
// and two tests use a tiny local server to prove the timeouts really fire
class UserServiceClientsTest {

    private static final String BASE = "http://user-service.test";
    private static final String KEY = "client-test-key";

    private static final String ADVISOR_JSON = """
            {"id": 5, "exists": true, "active": true, "userId": 12, "displayName": "Reem Ali",
             "title": "Senior Planner", "specialty": "MORTGAGES"}
            """;

    private MockRestServiceServer server;
    private RestAdvisorClient advisors;
    private RestUserClient users;
    private RestStrikeClient strikes;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = UserServiceClientConfig.restClientBuilder(BASE, KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        advisors = new RestAdvisorClient(restClient);
        users = new RestUserClient(restClient);
        strikes = new RestStrikeClient(restClient);
    }

    // strikes

    @Test
    void addStrikePostsWithTheKeyAndReadsTheResult() {
        server.expect(requestTo(BASE + "/internal/users/12/strikes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", KEY))
                .andRespond(withSuccess("{\"noShowStrikes\": 3, \"blacklisted\": true, \"somethingNew\": 1}", MediaType.APPLICATION_JSON));

        assertEquals(new StrikeResult(3, true), strikes.addStrike(12L));
        server.verify();
    }

    @Test
    void removeStrikeDeletesWithTheKeyAndReadsTheResult() {
        server.expect(requestTo(BASE + "/internal/users/12/strikes"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Internal-Key", KEY))
                .andRespond(withSuccess("{\"noShowStrikes\": 1, \"blacklisted\": false}", MediaType.APPLICATION_JSON));

        assertEquals(new StrikeResult(1, false), strikes.removeStrike(12L));
        server.verify();
    }

    @Test
    void strikeCallsMapNotFoundAndFailuresLikeTheOtherCalls() {
        server.expect(requestTo(BASE + "/internal/users/9/strikes")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/internal/users/10/strikes")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(BASE + "/internal/users/11/strikes")).andRespond(withSuccess()); // no body at all

        assertThrows(InformationNotFound.class, () -> strikes.addStrike(9L));
        assertThrows(ServiceUnavailable.class, () -> strikes.addStrike(10L));
        assertThrows(ServiceUnavailable.class, () -> strikes.removeStrike(11L));
    }

    // the calls

    @Test
    void getByIdSendsTheKeyAndReadsTheAdvisor() {
        server.expect(requestTo(BASE + "/internal/advisors/5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", KEY))
                .andRespond(withSuccess(ADVISOR_JSON, MediaType.APPLICATION_JSON));

        AdvisorInfo advisor = advisors.getById(5L);

        assertEquals(new AdvisorInfo(5L, 12L, true, "Reem Ali", "Senior Planner", Specialty.MORTGAGES), advisor);
        server.verify();
    }

    @Test
    void getByUserIdSendsTheKeyAndReadsTheAdvisor() {
        server.expect(requestTo(BASE + "/internal/advisors/by-user/12"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", KEY))
                .andRespond(withSuccess(ADVISOR_JSON, MediaType.APPLICATION_JSON));

        assertEquals(5L, advisors.getByUserId(12L).id());
        server.verify();
    }

    @Test
    void lookupPostsTheIdsOnceEachWithTheKeyAndReadsTheMap() {
        server.expect(requestTo(BASE + "/internal/advisors/lookup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[5,6]"))
                .andRespond(withSuccess("{\"5\": " + ADVISOR_JSON + "}", MediaType.APPLICATION_JSON));

        Map<Long, AdvisorInfo> found = advisors.lookup(List.of(5L, 5L, 6L)); // 6 does not exist, so it is not in the answer

        assertEquals(1, found.size());
        assertEquals("Reem Ali", found.get(5L).displayName());
        server.verify();
    }

    @Test
    void namesPostsTheIdsWithTheKeyAndReadsTheMap() {
        server.expect(requestTo(BASE + "/internal/users/names"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", KEY))
                .andExpect(content().string("[3,4]"))
                .andRespond(withSuccess("{\"3\": \"Maryam Hasan\"}", MediaType.APPLICATION_JSON));

        Map<Long, String> names = users.names(List.of(3L, 4L));

        assertEquals(Map.of(3L, "Maryam Hasan"), names);
        server.verify();
    }

    @Test
    void getUserSendsTheKeyAndReadsTheStatusFields() {
        server.expect(requestTo(BASE + "/internal/users/12"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", KEY))
                .andRespond(withSuccess("""
                        {"id": 12, "email": "maryam@test.com", "role": "CLIENT", "status": "ACTIVE",
                         "blacklisted": true, "noShowStrikes": 3, "displayName": "Maryam Hasan", "somethingNew": 1}
                        """, MediaType.APPLICATION_JSON));

        UserInfo user = users.getById(12L);

        assertEquals(new UserInfo(12L, "maryam@test.com", "CLIENT", "ACTIVE", true, 3, "Maryam Hasan"), user);
        server.verify();
    }

    @Test
    void getUserNotFoundAndFailuresMapLikeTheOtherCalls() {
        server.expect(requestTo(BASE + "/internal/users/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/internal/users/10")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertEquals("User not found", assertThrows(InformationNotFound.class, () -> users.getById(9L)).getMessage());
        assertThrows(ServiceUnavailable.class, () -> users.getById(10L));
    }

    @Test
    void anEmptyListOfIdsIsAnEmptyMapWithoutACall() {
        assertTrue(advisors.lookup(List.of()).isEmpty());
        assertTrue(users.names(List.of()).isEmpty());
        server.verify(); // no request was expected, so none may have been made
    }

    // a 404 is "not found"

    @Test
    void aNotFoundFromTheUserServiceIsInformationNotFound() {
        server.expect(requestTo(BASE + "/internal/advisors/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/internal/advisors/by-user/9")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals("Advisor not found", assertThrows(InformationNotFound.class, () -> advisors.getById(9L)).getMessage());
        assertEquals("Advisor profile not found", assertThrows(InformationNotFound.class, () -> advisors.getByUserId(9L)).getMessage());
    }

    // anything else that goes wrong is "unavailable"

    @Test
    void anErrorStatusIsServiceUnavailable() {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE)) {
            server.reset();
            server.expect(requestTo(BASE + "/internal/advisors/5")).andRespond(withStatus(status));

            assertThrows(ServiceUnavailable.class, () -> advisors.getById(5L), "status " + status.value());
        }
    }

    @Test
    void aTimeoutOrABrokenConnectionIsServiceUnavailable() {
        server.expect(requestTo(BASE + "/internal/advisors/5"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(BASE + "/internal/users/names"))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));

        assertThrows(ServiceUnavailable.class, () -> advisors.getById(5L));
        assertThrows(ServiceUnavailable.class, () -> users.names(List.of(1L)));
    }

    @Test
    void anAnswerThatCannotBeReadIsServiceUnavailable() {
        server.expect(requestTo(BASE + "/internal/advisors/5"))
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/internal/advisors/by-user/5")).andRespond(withSuccess()); // no body at all

        assertThrows(ServiceUnavailable.class, () -> advisors.getById(5L));
        assertThrows(ServiceUnavailable.class, () -> advisors.getByUserId(5L));
    }

    // the timeouts really fire

    @Test
    void aSlowUserServiceIsCutOffAtTheReadTimeout() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(2_000); // far longer than the read timeout below
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        slow.start();
        try {
            RestClient restClient = UserServiceClientConfig.restClientBuilder(
                    "http://127.0.0.1:" + slow.getAddress().getPort(), KEY, Duration.ofSeconds(1), Duration.ofMillis(300)).build();

            long started = System.nanoTime();
            assertThrows(ServiceUnavailable.class, () -> new RestAdvisorClient(restClient).getById(1L));
            long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

            assertTrue(tookMs < 1_500, "gave up after " + tookMs + " ms, the read timeout did not fire");
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void aUserServiceThatIsNotListeningIsServiceUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        } // closed again, so nothing listens on that port
        RestClient restClient = UserServiceClientConfig.restClientBuilder(
                "http://127.0.0.1:" + closedPort, KEY, Duration.ofMillis(500), Duration.ofMillis(500)).build();

        assertThrows(ServiceUnavailable.class, () -> new RestUserClient(restClient).names(List.of(1L)));
    }

    @Test
    void theDefaultTimeoutsAreShort() {
        assertTrue(UserServiceClientConfig.CONNECT_TIMEOUT.compareTo(Duration.ofSeconds(5)) <= 0);
        assertTrue(UserServiceClientConfig.READ_TIMEOUT.compareTo(Duration.ofSeconds(10)) <= 0);
    }
}
