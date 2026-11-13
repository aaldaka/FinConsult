package com.example.userservice.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// asks the booking service to cancel the appointments of a deactivated advisor or client. It runs after the
// deactivation is committed (see AfterCommit) and never throws: if booking is down, answers with an error or times out,
// a warning is logged and the deactivation stays. The appointments are then left active; that gap is closed later by
// sending the deactivation over Kafka, so booking picks it up when it is back
@Slf4j
@Component
public class RestAppointmentCancellation implements AppointmentCancellation {

    static final String KEY_HEADER = "X-Internal-Key";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    record CancelRequest(String reason, Long adminId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CancelResult(int cancelled, int failed) {}

    private final RestClient restClient;

    @Autowired
    public RestAppointmentCancellation(@Value("${booking-service.url:http://localhost:8083}") String baseUrl,
                                       @Value("${internal.api-key:}") String internalKey) {
        this(restClientBuilder(baseUrl, internalKey, CONNECT_TIMEOUT, READ_TIMEOUT).build());
    }

    RestAppointmentCancellation(RestClient restClient) {
        this.restClient = restClient;
    }

    static RestClient.Builder restClientBuilder(String baseUrl, String internalKey, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(KEY_HEADER, internalKey)
                .requestFactory(requestFactory);
    }

    @Override
    public int cancelUpcomingOfClient(Long clientUserId, Long adminUserId, String reason) {
        return cancel("/internal/appointments/cancel-for-client/{id}", clientUserId, new CancelRequest(reason, adminUserId),
                "client", clientUserId);
    }

    // the interface passes the advisor's own user id here, not an admin id, so no admin id is sent
    @Override
    public int cancelUpcomingOfAdvisor(Long advisorId, Long advisorUserId, String reason) {
        return cancel("/internal/appointments/cancel-for-advisor/{id}", advisorId, new CancelRequest(reason, null),
                "advisor user", advisorUserId);
    }

    private int cancel(String path, Long pathId, CancelRequest body, String who, Long userId) {
        try {
            CancelResult result = restClient.post()
                    .uri(path, pathId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(CancelResult.class);
            if (result == null) {
                log.warn("Booking answered without a body when cancelling the appointments of {} {}", who, userId);
                return 0;
            }
            log.info("Booking cancelled {} appointment(s) of {} {}, {} failed", result.cancelled(), who, userId, result.failed());
            return result.cancelled();
        } catch (RuntimeException e) {
            log.warn("Could not cancel the appointments of {} {}, the deactivation is kept and they stay active: {}",
                    who, userId, e.getMessage());
            return 0;
        }
    }
}
