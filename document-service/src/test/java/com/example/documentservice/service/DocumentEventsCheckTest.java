package com.example.documentservice.service;

import com.example.documentservice.client.AdminInfo;
import com.example.documentservice.client.UserClient;
import com.example.documentservice.model.AdvisorApplication;
import com.example.documentservice.model.ApplicationCertificate;
import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.CertificateStatus;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.Specialty;
import com.example.documentservice.repository.AdvisorApplicationRepository;
import com.example.documentservice.repository.ApplicationCertificateRepository;
import com.example.documentservice.repository.KycDocumentRepository;
import com.example.documentservice.support.TestPeople;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.FileSystemUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// the background checks with the real Kafka publishers and the real AdminNotifier: only the KafkaTemplate, VirusTotal
// and the user service are mocks. When the checks of a batch are done the owner gets a STREAM_PUSH, and when a file
// was flagged every admin gets a DOCUMENT_FLAGGED notification. Runs on finconsult_test (TEST_DB_URL); the users come
// from TestPeople and are deleted afterwards with their documents and applications
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class DocumentEventsCheckTest {

    private static final Path UPLOADS = tempDir();
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] NOT_A_DOCUMENT = "just some text".getBytes(StandardCharsets.UTF_8);
    private static final long FIRST_ADMIN = 900_001L;
    private static final long SECOND_ADMIN = 900_002L;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("document-events-check-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void deleteTheUploads() throws IOException {
        FileSystemUtils.deleteRecursively(UPLOADS);
    }

    @Autowired DocumentProcessor processor;
    @Autowired FileStorageService fileStorageService;
    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired AdvisorApplicationRepository applicationRepository;
    @Autowired ApplicationCertificateRepository certificateRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean VirusTotalService virusTotalService;
    @MockitoBean UserClient userClient;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private TestPeople people;
    private long client;

    @BeforeEach
    void setUp() {
        people = new TestPeople(jdbcTemplate);
        client = people.newClient();
        when(virusTotalService.lookup(anyString())).thenReturn(ScanResult.clean());
        when(userClient.admins()).thenReturn(List.of(
                new AdminInfo(FIRST_ADMIN, "a@test.com", "A"), new AdminInfo(SECOND_ADMIN, "b@test.com", "B")));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        people.deleteCreated();
    }

    // helpers

    private record Sent(String topic, String key, JsonNode event) {
        String summary() {
            JsonNode payload = event.get("payload");
            String what = payload.has("eventName") ? payload.get("eventName").asString() : payload.get("notificationType").asString();
            return topic + " " + key + " " + event.get("type").asString() + " " + what;
        }

        JsonNode payload() {
            return event.get("payload");
        }
    }

    private List<Sent> sent() {
        ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, atLeast(1)).send(topics.capture(), keys.capture(), values.capture());
        List<Sent> sent = new ArrayList<>();
        for (int i = 0; i < topics.getAllValues().size(); i++) {
            sent.add(new Sent(topics.getAllValues().get(i), keys.getAllValues().get(i), objectMapper.readTree(values.getAllValues().get(i))));
        }
        return sent;
    }

    private List<String> summaries(List<Sent> sent) {
        return sent.stream().map(Sent::summary).toList();
    }

    // the magic bytes followed by something random, so no two tests ever upload the same file
    private static byte[] file(byte[] magic) {
        byte[] tail = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[magic.length + tail.length];
        System.arraycopy(magic, 0, bytes, 0, magic.length);
        System.arraycopy(tail, 0, bytes, magic.length, tail.length);
        return bytes;
    }

    private String store(byte[] bytes, String subfolder) {
        return fileStorageService.store(new MockMultipartFile("file", "upload.pdf", "application/pdf", bytes), subfolder);
    }

    private KycDocument document(DocumentType type, byte[] bytes) {
        KycDocument doc = new KycDocument();
        doc.setUserId(client);
        doc.setDocumentType(type);
        doc.setFilePath(store(bytes, "kyc_documents"));
        doc.setOriginalFileName("upload.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(bytes.length);
        doc.setStatus(KycStatus.PENDING);
        return kycDocumentRepository.save(doc);
    }

    private AdvisorApplication application() {
        AdvisorApplication application = new AdvisorApplication();
        application.setUserId(client);
        application.setTitle("Planner");
        application.setSpecialty(Specialty.GENERAL);
        application.setYearsOfExperience(3);
        application.setStatus(KycStatus.PENDING);
        return applicationRepository.save(application);
    }

    private ApplicationCertificate certificate(AdvisorApplication application, byte[] bytes) {
        ApplicationCertificate certificate = new ApplicationCertificate();
        certificate.setApplication(application);
        certificate.setFilePath(store(bytes, "advisor_applications"));
        certificate.setOriginalFileName("cert.pdf");
        certificate.setContentType("application/pdf");
        certificate.setFileSize(bytes.length);
        certificate.setStatus(CertificateStatus.PROCESSING);
        return certificateRepository.save(certificate);
    }

    // KYC_CHECKED

    @Test
    void cleanKycDocumentsSendTheirOwnerKycCheckedWithTheIdsAndNoAlert() throws Exception {
        KycDocument first = document(DocumentType.PASSPORT, file(PDF));
        KycDocument second = document(DocumentType.NATIONAL_ID, file(PDF));

        processor.processKycAll(client, List.of(first.getId(), second.getId())).get(30, TimeUnit.SECONDS);

        List<Sent> sent = sent();
        assertEquals(List.of("notification-events " + client + " STREAM_PUSH KYC_CHECKED"), summaries(sent));
        JsonNode ids = sent.get(0).payload().get("data").get("documentIds");
        assertEquals(List.of(first.getId(), second.getId()), List.of(ids.get(0).asLong(), ids.get(1).asLong()));
    }

    @Test
    void aFlaggedKycDocumentAlsoNotifiesEveryAdmin() throws Exception {
        KycDocument fake = document(DocumentType.PASSPORT, NOT_A_DOCUMENT);

        processor.processKycAll(client, List.of(fake.getId())).get(30, TimeUnit.SECONDS);

        List<Sent> sent = sent();
        assertEquals(List.of(
                "notification-events " + client + " STREAM_PUSH KYC_CHECKED",
                "notification-events " + FIRST_ADMIN + " NOTIFICATION DOCUMENT_FLAGGED",
                "notification-events " + SECOND_ADMIN + " NOTIFICATION DOCUMENT_FLAGGED"), summaries(sent));
        assertTrue(sent.get(1).payload().get("message").asString().startsWith("1 KYC document(s) from "));
        assertTrue(sent.get(1).payload().get("relatedAppointmentId").isNull());
    }

    // CERTIFICATES_CHECKED

    @Test
    void checkedCertificatesSendTheApplicantCertificatesCheckedWithTheApplicationId() throws Exception {
        AdvisorApplication application = application();
        ApplicationCertificate valid = certificate(application, file(PDF));

        processor.processAll(application.getId(), client, List.of(valid.getId())).get(30, TimeUnit.SECONDS);

        List<Sent> sent = sent();
        assertEquals(List.of("notification-events " + client + " STREAM_PUSH CERTIFICATES_CHECKED"), summaries(sent));
        assertEquals(application.getId(), sent.get(0).payload().get("data").get("applicationId").asLong());
    }

    @Test
    void aFlaggedCertificateAlsoNotifiesEveryAdmin() throws Exception {
        AdvisorApplication application = application();
        ApplicationCertificate fake = certificate(application, NOT_A_DOCUMENT);

        processor.processAll(application.getId(), client, List.of(fake.getId())).get(30, TimeUnit.SECONDS);

        assertEquals(List.of(
                "notification-events " + client + " STREAM_PUSH CERTIFICATES_CHECKED",
                "notification-events " + FIRST_ADMIN + " NOTIFICATION DOCUMENT_FLAGGED",
                "notification-events " + SECOND_ADMIN + " NOTIFICATION DOCUMENT_FLAGGED"), summaries(sent()));
    }
}
