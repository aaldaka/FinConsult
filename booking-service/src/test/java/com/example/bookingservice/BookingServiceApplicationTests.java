package com.example.bookingservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// also checks, with ddl-auto=validate, that the appointments, availability_slots and service_types tables match the entities
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class BookingServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
