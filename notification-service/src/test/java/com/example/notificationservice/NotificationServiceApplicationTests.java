package com.example.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// also checks, with ddl-auto=validate, that the notifications table matches the entity
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
