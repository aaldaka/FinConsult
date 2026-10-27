package com.example.finConsult.service;

import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.ServiceType;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.security.JWTUtils;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Concurrent REST requests: two real HTTP calls to POST /appointments/book/{slotId} at the same moment.
 * The tokens are generated directly, so the login rate limit (5 per minute) is not touched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestConcurrencyTest extends ConcurrencyTestSupport {

    @LocalServerPort int port;
    @Autowired JWTUtils jwtUtils;

    private final HttpClient http = HttpClient.newHttpClient();

    private int book(User client, Long slotId, Long serviceId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/appointments/book/" + slotId))
                .header("Authorization", "Bearer " + jwtUtils.generateToken(client.getEmail()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"serviceTypeId\": " + serviceId + "}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @RepeatedTest(5)
    void twoRequestsOneSlotGive201And409() throws Exception {
        Advisor advisor = seededAdvisor();
        ServiceType service = freeService();
        AvailabilitySlot slot = newFutureSlot(advisor);
        User first = newClient();
        User second = newClient();

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        Result result = runAtOnce(List.of(
                () -> { try { statuses.add(book(first, slot.getId(), service.getId())); } catch (Exception e) { throw new RuntimeException(e); } },
                () -> { try { statuses.add(book(second, slot.getId(), service.getId())); } catch (Exception e) { throw new RuntimeException(e); } }));

        Collections.sort(statuses);
        System.out.println("HTTP statuses: " + statuses);

        assertEquals(0, result.other());
        assertEquals(List.of(201, 409), statuses, "one request created the appointment, the other got a conflict");
        assertEquals(1, appointmentRepository.findByAdvisorAndStatusIn(advisor,
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)).stream()
                .filter(a -> a.getSlot().getId().equals(slot.getId())).count());
    }
}
