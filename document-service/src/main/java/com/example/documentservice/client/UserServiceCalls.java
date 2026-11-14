package com.example.documentservice.client;

import com.example.documentservice.exception.InformationNotFound;
import com.example.documentservice.exception.ServiceUnavailable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

@Slf4j
final class UserServiceCalls {

    static final String KEY_HEADER = "X-Internal-Key";

    private UserServiceCalls() {
    }

    static <T> T call(Supplier<T> request, String notFoundMessage) {
        T result;
        try {
            result = request.get();
        } catch (HttpClientErrorException.NotFound e) {
            throw new InformationNotFound(notFoundMessage);
        } catch (RestClientException e) { // an error status, a timeout, a refused connection or a body that cannot be read
            log.warn("The user service call failed: {}", e.getMessage());
            throw new ServiceUnavailable("The user service is not available, please try again later");
        }
        if (result == null) {
            log.warn("The user service answered with an empty body");
            throw new ServiceUnavailable("The user service is not available, please try again later");
        }
        return result;
    }
}
