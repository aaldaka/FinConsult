package com.example.bookingservice.client;

import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.model.enums.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// asks the document service whether the client has an approved KYC document. Anything but a clear answer (the service
// unreachable, an error status, a timeout, an empty or unreadable body) is a 503: it is never taken as approved
@Slf4j
@Component
public class RestKycCheck implements KycCheck {

    private static final String UNAVAILABLE = "The document service is not available, please try again later";

    private final RestClient restClient;

    public RestKycCheck(@Qualifier("documentServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean isApproved(long userId, DocumentType requiredType) {
        KycApproval answer;
        try {
            answer = restClient.get()
                    .uri(uri -> {
                        uri.path("/internal/kyc/approved").queryParam("userId", userId);
                        if (requiredType != null) {
                            uri.queryParam("documentType", requiredType);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(KycApproval.class);
        } catch (RestClientException e) {
            log.warn("The document service call failed: {}", e.getMessage());
            throw new ServiceUnavailable(UNAVAILABLE);
        }
        if (answer == null || answer.approved() == null) {
            log.warn("The document service answered without a decision for user {}", userId);
            throw new ServiceUnavailable(UNAVAILABLE);
        }
        return answer.approved();
    }
}
