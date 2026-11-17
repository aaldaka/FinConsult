package com.example.documentservice.cucumber;

import com.example.documentservice.DocumentServiceApplication;
import com.example.documentservice.client.UserClient;
import com.example.documentservice.integration.AuditRecorder;
import com.example.documentservice.integration.NotificationPublisher;
import com.example.documentservice.service.DocumentProcessor;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

// the app runs on a random port and the scenarios call it over http with REST Assured. It uses finconsult_test
// (TEST_DB_URL) like the other tests. The user service, the background file checks and the notification and audit
// publishers are mocks, the same as in the controller tests, so nothing leaves the test
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = DocumentServiceApplication.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
public class CucumberSpringConfiguration {

    static final Path UPLOADS = tempDir();

    @MockitoBean UserClient userClient;
    @MockitoBean DocumentProcessor documentProcessor;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("kyc-cucumber");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
