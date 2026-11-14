package com.example.documentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// also checks, with ddl-auto=validate, that the kyc_documents, advisor_applications and application_certificates
// tables match the entities
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class DocumentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
