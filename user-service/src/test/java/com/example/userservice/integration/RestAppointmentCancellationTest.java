package com.example.userservice.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// no Spring context, no database and no real booking service: MockRestServiceServer plays booking, and two tests use a
// closed port and a tiny local server that answers too late, to prove an unreachable or slow booking never throws
@ExtendWith(OutputCaptureExtension.class)
class RestAppointmentCancellationTest {

    private static final String BASE = "http://booking-service.test";
    private static final String KEY = "cancellation-test-key";

    private MockRestServiceServer booking;
    private RestAppointmentCancellation cancellation;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestAppointmentCancellation.restClientBuilder(BASE, KEY,
                RestAppointmentCancellation.CONNECT_TIMEOUT, RestAppointmentCancellation.READ_TIMEOUT);
        booking = MockRestServiceServer.bindTo(builder).build();
        cancellation = new RestAppointmentCancellation(builder.build());
    }

    @Test
    void aClientCancellationSendsTheKeyAndTheBodyAndLogsBothCounts(CapturedOutput output) {
        booking.expect(requestTo(BASE + "/internal/appointments/cancel-for-client/10"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"reason\": \"Client account was deactivated\", \"adminId\": 99}", true))
                .andRespond(withSuccess("{\"cancelled\": 2, \"failed\": 1}", MediaType.APPLICATION_JSON));

        int cancelled = cancellation.cancelUpcomingOfClient(10L, 99L, "Client account was deactivated");

        assertEquals(2, cancelled);
        booking.verify();
        assertTrue(output.getOut().contains("Booking cancelled 2 appointment(s) of client 10, 1 failed"));
    }

    @Test
    void anAdvisorCancellationGoesToTheAdvisorEndpointWithTheReasonAndNoAdminId(CapturedOutput output) {
        booking.expect(requestTo(BASE + "/internal/appointments/cancel-for-advisor/5"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", KEY))
                .andExpect(content().json("{\"reason\": \"Advisor is no longer available: left\", \"adminId\": null}", true))
                .andRespond(withSuccess("{\"cancelled\": 3, \"failed\": 0}", MediaType.APPLICATION_JSON));

        assertEquals(3, cancellation.cancelUpcomingOfAdvisor(5L, 105L, "Advisor is no longer available: left"));
        booking.verify();
        assertTrue(output.getOut().contains("Booking cancelled 3 appointment(s) of advisor user 105, 0 failed"));
    }

    @Test
    void aNullReasonAndANullAdminAreSentAsNulls() {
        booking.expect(requestTo(BASE + "/internal/appointments/cancel-for-client/10"))
                .andExpect(content().json("{\"reason\": null, \"adminId\": null}", true))
                .andRespond(withSuccess("{\"cancelled\": 0, \"failed\": 0}", MediaType.APPLICATION_JSON));

        assertEquals(0, cancellation.cancelUpcomingOfClient(10L, null, null));
        booking.verify();
    }

    @Test
    void aServerErrorDoesNotThrowAndLogsAWarningWithTheUserId(CapturedOutput output) {
        booking.expect(requestTo(BASE + "/internal/appointments/cancel-for-client/10")).andRespond(withServerError());

        int cancelled = assertDoesNotThrow(() -> cancellation.cancelUpcomingOfClient(10L, 99L, "Client account was deactivated"));

        assertEquals(0, cancelled);
        assertTrue(output.getOut().contains("Could not cancel the appointments of client 10"));
    }

    @Test
    void aRefusedKeyOrAnEmptyAnswerDoesNotThrow() {
        booking.expect(requestTo(BASE + "/internal/appointments/cancel-for-advisor/5")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        booking.expect(requestTo(BASE + "/internal/appointments/cancel-for-advisor/5")).andRespond(withSuccess());

        assertEquals(0, assertDoesNotThrow(() -> cancellation.cancelUpcomingOfAdvisor(5L, 105L, "x"))); // 403
        assertEquals(0, assertDoesNotThrow(() -> cancellation.cancelUpcomingOfAdvisor(5L, 105L, "x"))); // empty body
        booking.verify();
    }

    @Test
    void anUnreachableBookingDoesNotThrow(CapturedOutput output) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort(); // closed again before the call, so nothing listens there
        }
        RestAppointmentCancellation unreachable = new RestAppointmentCancellation(RestAppointmentCancellation.restClientBuilder(
                "http://127.0.0.1:" + port, KEY, Duration.ofMillis(500), Duration.ofMillis(500)).build());

        assertEquals(0, assertDoesNotThrow(() -> unreachable.cancelUpcomingOfClient(10L, 99L, "Client account was deactivated")));
        assertTrue(output.getOut().contains("Could not cancel the appointments of client 10"));
    }

    @Test
    void aBookingThatAnswersTooLateTimesOutWithoutThrowing(CapturedOutput output) throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        slow.start();
        try {
            RestAppointmentCancellation timingOut = new RestAppointmentCancellation(RestAppointmentCancellation.restClientBuilder(
                    "http://127.0.0.1:" + slow.getAddress().getPort(), KEY, Duration.ofMillis(500), Duration.ofMillis(300)).build());

            long started = System.nanoTime();
            assertEquals(0, assertDoesNotThrow(() -> timingOut.cancelUpcomingOfAdvisor(5L, 105L, "x")));
            assertTrue(System.nanoTime() - started < Duration.ofMillis(1_500).toNanos(), "the read timeout cut the call short");
            assertTrue(output.getOut().contains("Could not cancel the appointments of advisor user 105"));
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void theRealTimeoutsAreShort() {
        assertTrue(RestAppointmentCancellation.CONNECT_TIMEOUT.compareTo(Duration.ofSeconds(5)) <= 0);
        assertTrue(RestAppointmentCancellation.READ_TIMEOUT.compareTo(Duration.ofSeconds(10)) <= 0);
    }
}
