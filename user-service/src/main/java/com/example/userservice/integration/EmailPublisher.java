package com.example.userservice.integration;

// the emails this service asks for; notification-service sends them. The link is finished here and is a secret
public interface EmailPublisher {

    void verification(String to, String link);

    void passwordReset(String to, String link, int expiresInMinutes);
}
