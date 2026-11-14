package com.example.documentservice.integration;

import java.util.Map;

// tells a user's open browser that the background checks of their files are done (KYC_CHECKED, CERTIFICATES_CHECKED)
public interface CheckEventPublisher {

    void push(long userId, Map<String, Object> event);
}
