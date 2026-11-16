package com.example.documentservice.service;

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
import com.example.documentservice.support.FakeUserService;
import com.example.documentservice.support.TestPeople;
import com.example.documentservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// the KYC and advisor application endpoints with the real Kafka publishers: only the KafkaTemplate is a mock, it
// records what was sent and whether a database transaction was still open at that moment. Runs on finconsult_test
// (TEST_DB_URL); the users come from TestPeople and are deleted afterwards with their documents and applications,
// the user service is FakeUserService and the background checks are mocked (DocumentEventsCheckTest covers those)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class DocumentEventsTest {

    private static final Path UPLOADS = tempDir();

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("document-events-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void deleteTheUploads() throws IOException {
        FileSystemUtils.deleteRecursively(UPLOADS);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired AdvisorApplicationRepository applicationRepository;
    @Autowired ApplicationCertificateRepository certificateRepository;
    @Autowired FileStorageService fileStorageService;

    @MockitoBean UserClient userClient;
    @MockitoBean DocumentProcessor documentProcessor;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    @Value("${jwt.secret}") String jwtSecret;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<String> sentInsideATransaction = new CopyOnWriteArrayList<>();
    private TestPeople people;
    private long client;
    private long admin;

    @BeforeEach
    void setUp() {
        people = new TestPeople(jdbcTemplate);
        new FakeUserService(userClient, () -> {});
        client = people.newClient();
        admin = people.newAdmin();
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                sentInsideATransaction.add(invocation.getArgument(0));
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        people.deleteCreated();
    }

    // helpers

    private record Sent(String topic, String key, JsonNode event) {
        String summary() {
            JsonNode payload = event.get("payload");
            String what = payload.has("action") ? payload.get("action").asString() : payload.get("notificationType").asString();
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
        assertTrue(sentInsideATransaction.isEmpty(), "sent before the commit: " + sentInsideATransaction);
        return sent;
    }

    private List<String> summaries(List<Sent> sent) {
        return sent.stream().map(Sent::summary).toList();
    }

    private String bearer(long userId, String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, role);
    }

    private static MockMultipartFile pdf(String part) {
        return new MockMultipartFile(part, "file.pdf", "application/pdf", ("%PDF-1.4 " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    }

    private long submittedKyc() throws Exception {
        String body = mockMvc.perform(multipart("/kyc").file(pdf("file")).param("documentType", "PASSPORT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private AdvisorApplication application() {
        AdvisorApplication application = new AdvisorApplication();
        application.setUserId(client);
        application.setTitle("Planner");
        application.setSpecialty(Specialty.INSURANCE);
        application.setYearsOfExperience(6);
        application.setStatus(KycStatus.PENDING);
        return applicationRepository.save(application);
    }

    private ApplicationCertificate certificate(AdvisorApplication application, CertificateStatus status) {
        ApplicationCertificate certificate = new ApplicationCertificate();
        certificate.setApplication(application);
        certificate.setFilePath(fileStorageService.store(pdf("file"), "advisor_applications"));
        certificate.setOriginalFileName("cert.pdf");
        certificate.setContentType("application/pdf");
        certificate.setFileSize(20);
        certificate.setStatus(status);
        return certificateRepository.save(certificate);
    }

    private void giveApprovedIdentity() {
        KycDocument doc = new KycDocument();
        doc.setUserId(client);
        doc.setDocumentType(DocumentType.PASSPORT);
        doc.setFilePath("/uploads/none.pdf");
        doc.setOriginalFileName("id.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(10);
        doc.setStatus(KycStatus.APPROVED);
        kycDocumentRepository.save(doc);
    }

    // KYC

    @Test
    void submittingAKycDocumentAuditsIt() throws Exception {
        long id = submittedKyc();

        List<Sent> sent = sent();
        assertEquals(List.of("audit-events KycDocument:" + id + " AUDIT KYC_SUBMITTED"), summaries(sent));
        assertEquals(client, sent.get(0).payload().get("actorId").asLong());
    }

    @Test
    void replacingARejectedKycDocumentAuditsTheResubmission() throws Exception {
        long id = submittedKyc();
        jdbcTemplate.update("UPDATE kyc_documents SET detected_type = 'PDF', sha256 = ?, status = 'REJECTED', rejection_reason = 'blurry' WHERE id = ?",
                UUID.randomUUID().toString(), id);
        clearInvocations(kafkaTemplate);

        mockMvc.perform(multipart(HttpMethod.PUT, "/kyc/" + id + "/file").file(pdf("file"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isOk());

        assertEquals(List.of("audit-events KycDocument:" + id + " AUDIT KYC_RESUBMITTED"), summaries(sent()));
    }

    @Test
    void approvingAKycDocumentTellsTheClientAndAuditsIt() throws Exception {
        long id = submittedKyc();
        clearInvocations(kafkaTemplate);

        mockMvc.perform(patch("/kyc/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk());

        List<Sent> sent = sent();
        assertEquals(List.of(
                "notification-events " + client + " NOTIFICATION KYC_REVIEWED",
                "audit-events KycDocument:" + id + " AUDIT KYC_APPROVED"), summaries(sent));
        assertEquals("Your PASSPORT document has been approved.", sent.get(0).payload().get("message").asString());
        assertEquals(admin, sent.get(1).payload().get("actorId").asLong());
    }

    @Test
    void rejectingAKycDocumentTellsTheClientWithTheReasonAndAuditsIt() throws Exception {
        long id = submittedKyc();
        clearInvocations(kafkaTemplate);

        mockMvc.perform(patch("/kyc/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"blurry photo\"}"))
                .andExpect(status().isOk());

        List<Sent> sent = sent();
        assertEquals(List.of(
                "notification-events " + client + " NOTIFICATION KYC_REVIEWED",
                "audit-events KycDocument:" + id + " AUDIT KYC_REJECTED"), summaries(sent));
        assertEquals("Your PASSPORT document was rejected. Reason: blurry photo", sent.get(0).payload().get("message").asString());
    }

    // advisor applications

    @Test
    void submittingAnApplicationAuditsIt() throws Exception {
        giveApprovedIdentity();
        MockMultipartFile application = new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"title\": \"Retirement Planner\", \"specialty\": \"RETIREMENT_PLANNING\", \"yearsOfExperience\": 4}".getBytes(StandardCharsets.UTF_8));

        String body = mockMvc.perform(multipart("/advisor-applications").file(application).file(pdf("certificates"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        List<Sent> sent = sent();
        assertEquals(List.of("audit-events AdvisorApplication:" + id + " AUDIT ADVISOR_APPLICATION_SUBMITTED"), summaries(sent));
        assertEquals(FakeUserService.name(client) + " applied to become an advisor", sent.get(0).payload().get("details").asString());
    }

    @Test
    void approvingAnApplicationTellsTheApplicantAndAuditsIt() throws Exception {
        AdvisorApplication application = application();
        certificate(application, CertificateStatus.VALID);

        mockMvc.perform(patch("/advisor-applications/" + application.getId() + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk());

        List<Sent> sent = sent();
        assertEquals(List.of(
                "notification-events " + client + " NOTIFICATION ADVISOR_APPLICATION_REVIEWED",
                "audit-events AdvisorApplication:" + application.getId() + " AUDIT ADVISOR_APPLICATION_APPROVED"), summaries(sent));
        assertEquals(admin, sent.get(1).payload().get("actorId").asLong());
    }

    @Test
    void rejectingAnApplicationTellsTheApplicantAndAuditsIt() throws Exception {
        AdvisorApplication application = application();

        mockMvc.perform(patch("/advisor-applications/" + application.getId() + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"not enough experience\"}"))
                .andExpect(status().isOk());

        List<Sent> sent = sent();
        assertEquals(List.of(
                "notification-events " + client + " NOTIFICATION ADVISOR_APPLICATION_REVIEWED",
                "audit-events AdvisorApplication:" + application.getId() + " AUDIT ADVISOR_APPLICATION_REJECTED"), summaries(sent));
        assertEquals("Your advisor application was rejected. Reason: not enough experience", sent.get(0).payload().get("message").asString());
    }

    @Test
    void replacingACertificateAuditsItOnTheCertificate() throws Exception {
        AdvisorApplication application = application();
        ApplicationCertificate flagged = certificate(application, CertificateStatus.FLAGGED);

        mockMvc.perform(multipart(HttpMethod.PUT, "/advisor-applications/certificates/" + flagged.getId() + "/file").file(pdf("file"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isOk());

        List<Sent> sent = sent();
        assertEquals(List.of("audit-events ApplicationCertificate:" + flagged.getId() + " AUDIT CERTIFICATE_REPLACED"), summaries(sent));
        assertEquals(client, sent.get(0).payload().get("actorId").asLong());
    }
}
