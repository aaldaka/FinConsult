package com.example.bookingservice.client;

import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.model.enums.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// no Spring context and no real document service: MockRestServiceServer plays it, and one test uses a closed port
class RestKycCheckTest {

    private static final String BASE = "http://document-service.test";
    private static final String KEY = "kyc-test-key";
    private static final String DOWN = "The document service is not available, please try again later";

    private MockRestServiceServer server;
    private RestKycCheck kycCheck;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = UserServiceClientConfig.restClientBuilder(BASE, KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        kycCheck = new RestKycCheck(builder.build());
    }

    @Test
    void aTypeIsAskedForWithTheKeyAndTheAnswerIsRead() {
        server.expect(requestTo(BASE + "/internal/kyc/approved?userId=12&documentType=INCOME_PROOF"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", KEY))
                .andRespond(withSuccess("{\"approved\": true, \"somethingNew\": 1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/internal/kyc/approved?userId=12&documentType=PASSPORT"))
                .andRespond(withSuccess("{\"approved\": false}", MediaType.APPLICATION_JSON));

        assertTrue(kycCheck.isApproved(12L, DocumentType.INCOME_PROOF));
        assertFalse(kycCheck.isApproved(12L, DocumentType.PASSPORT));
        server.verify();
    }

    @Test
    void withoutATypeNoDocumentTypeIsSent() {
        server.expect(requestTo(BASE + "/internal/kyc/approved?userId=12"))
                .andRespond(withSuccess("{\"approved\": true}", MediaType.APPLICATION_JSON));

        assertTrue(kycCheck.isApproved(12L, null));
        server.verify();
    }

    @Test
    void anErrorAnEmptyBodyOrAMissingAnswerIs503AndNeverApproved() {
        server.expect(requestTo(BASE + "/internal/kyc/approved?userId=12")).andRespond(withServerError());
        server.expect(requestTo(BASE + "/internal/kyc/approved?userId=12")).andRespond(withSuccess());
        server.expect(requestTo(BASE + "/internal/kyc/approved?userId=12")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/internal/kyc/approved?userId=12")).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        for (int i = 0; i < 4; i++) {
            assertEquals(DOWN, assertThrows(ServiceUnavailable.class, () -> kycCheck.isApproved(12L, null)).getMessage());
        }
        server.verify();
    }

    @Test
    void anUnreachableDocumentServiceIs503() {
        RestKycCheck unreachable = new RestKycCheck(UserServiceClientConfig.restClientBuilder("http://127.0.0.1:1", KEY,
                Duration.ofMillis(500), Duration.ofMillis(500)).build());

        assertThrows(ServiceUnavailable.class, () -> unreachable.isApproved(12L, DocumentType.PASSPORT));
    }
}
