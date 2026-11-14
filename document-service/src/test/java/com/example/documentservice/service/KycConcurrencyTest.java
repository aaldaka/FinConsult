package com.example.documentservice.service;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.integration.AdminNotifier;
import com.example.documentservice.integration.AuditRecorder;
import com.example.documentservice.integration.CheckEventPublisher;
import com.example.documentservice.integration.NotificationPublisher;
import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.repository.KycDocumentRepository;
import com.example.documentservice.support.RaceRunner;
import com.example.documentservice.support.TestPeople;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// the main app's KycConcurrencyTest, ported. Runs on finconsult_test (TEST_DB_URL), no @Transactional on the class, so
// the unique index and the version checks are the real ones. Each test makes its own users with TestPeople and deletes
// them with their documents afterwards, files go into a temp dir, and the user service and the stubs are mocks
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class KycConcurrencyTest {

    private static final Path UPLOADS = tempDir();

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("kyc-concurrency-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void deleteTheUploads() throws IOException {
        FileSystemUtils.deleteRecursively(UPLOADS);
    }

    @Autowired KycService kycService;
    @Autowired DocumentProcessor documentProcessor;
    @Autowired FileStorageService fileStorageService;
    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean UserClient userClient;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean CheckEventPublisher checkEvents;
    @MockitoBean AdminNotifier adminNotifier;
    @MockitoBean VirusTotalService virusTotalService;

    private TestPeople people;
    private long admin;

    @BeforeEach
    void setUp() {
        people = new TestPeople(jdbcTemplate);
        admin = people.newAdmin();
        when(userClient.lookup(any())).thenReturn(Map.of());
        when(virusTotalService.lookup(anyString())).thenReturn(ScanResult.clean());
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        people.deleteCreated();
    }

    // helpers

    private MockMultipartFile newPdf() {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf",
                ("%PDF-1.4 " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    }

    private KycDocument newKycDocument(long userId, DocumentType type, KycStatus status, String path) {
        KycDocument doc = new KycDocument();
        doc.setUserId(userId);
        doc.setDocumentType(type);
        doc.setFilePath(path);
        doc.setOriginalFileName("doc.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(100);
        doc.setStatus(status);
        return doc;
    }

    // submit starts the background check; wait for it so the cleanup does not race it
    private void awaitChecksOf(long userId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            boolean running = kycDocumentRepository.findByUserId(userId).stream()
                    .anyMatch(d -> d.getDetectedType() == null && d.getFlagReason() == null);
            if (!running) {
                return;
            }
            Thread.sleep(100);
        }
    }

    // one client uploads the same document type twice at once: only one pending document may exist
    @RepeatedTest(10)
    void doubleUploadCreatesOnePendingDocument() throws Exception {
        long client = people.newClient();

        RaceRunner.Result result = RaceRunner.runAtOnce(2, () -> kycService.submit(client, newPdf(), DocumentType.NATIONAL_ID));
        awaitChecksOf(client);

        long pending = kycDocumentRepository.findByUserId(client).stream()
                .filter(d -> d.getStatus() == KycStatus.PENDING && d.getDocumentType() == DocumentType.NATIONAL_ID)
                .count();

        assertEquals(0, result.other());
        assertEquals(1, result.successes());
        assertEquals(1, result.refused());
        assertEquals(1, pending);
    }

    // the check finishes AFTER the admin approved the document, and it flagged it:
    // the admin's decision and the flag are both kept, and the document goes back to PENDING
    @Test
    void flaggedDocumentApprovedDuringTheCheckGoesBackToPending() {
        long client = people.newClient();
        KycDocument saved = kycDocumentRepository.save(newKycDocument(client, DocumentType.PASSPORT, KycStatus.PENDING, "/uploads/none.pdf"));
        Long id = saved.getId();

        KycDocument staleCopy = kycDocumentRepository.findById(id).orElseThrow();   // what the worker loaded
        kycService.approve(id, admin);                                               // the admin approves meanwhile

        staleCopy.setDetectedType("PDF");
        staleCopy.setSha256("abc123");
        staleCopy.setFlagReason("Matches malware");
        documentProcessor.saveCheckResult(id, staleCopy);                            // the worker saves its result

        KycDocument result = kycDocumentRepository.findById(id).orElseThrow();
        assertEquals(KycStatus.PENDING, result.getStatus(), "approved but flagged: back to review");
        assertNull(result.getReviewedBy());
        assertEquals("Matches malware", result.getFlagReason(), "the check result is not lost");
        assertEquals("abc123", result.getSha256());
    }

    // the admin approves before the FIRST save (the hash save): that save takes the conflict,
    // so the final save with the flag goes through without one and must still send it back to review
    @Test
    void approvedBeforeTheHashSaveAndThenFlaggedGoesBackToPending() {
        long client = people.newClient();
        KycDocument saved = kycDocumentRepository.save(newKycDocument(client, DocumentType.PASSPORT, KycStatus.PENDING, "/uploads/none.pdf"));
        Long id = saved.getId();

        KycDocument staleCopy = kycDocumentRepository.findById(id).orElseThrow();   // what the worker loaded
        kycService.approve(id, admin);                                               // the admin approves meanwhile

        staleCopy.setDetectedType("PDF");
        staleCopy.setSha256("ghi789");
        KycDocument afterHashSave = documentProcessor.saveCheckResult(id, staleCopy); // first save, no flag yet
        assertEquals(KycStatus.APPROVED, afterHashSave.getStatus(), "no flag yet, the approval stays");

        afterHashSave.setFlagReason("Matches malware");                              // the check flags it
        documentProcessor.saveCheckResult(id, afterHashSave);                         // final save, no conflict this time

        KycDocument result = kycDocumentRepository.findById(id).orElseThrow();
        assertEquals(KycStatus.PENDING, result.getStatus(), "approved but flagged: back to review");
        assertNull(result.getReviewedBy());
        assertNull(result.getReviewedAt());
        assertEquals("Matches malware", result.getFlagReason());
    }

    // same race, but the check found nothing: the approval stays and the type/hash are added
    @Test
    void cleanDocumentApprovedDuringTheCheckStaysApproved() {
        long client = people.newClient();
        KycDocument saved = kycDocumentRepository.save(newKycDocument(client, DocumentType.PASSPORT, KycStatus.PENDING, "/uploads/none.pdf"));
        Long id = saved.getId();

        KycDocument staleCopy = kycDocumentRepository.findById(id).orElseThrow();
        kycService.approve(id, admin);

        staleCopy.setDetectedType("PDF");
        staleCopy.setSha256("def456");
        documentProcessor.saveCheckResult(id, staleCopy);

        KycDocument result = kycDocumentRepository.findById(id).orElseThrow();
        assertEquals(KycStatus.APPROVED, result.getStatus());
        assertNotNull(result.getReviewedBy());
        assertNull(result.getFlagReason());
        assertEquals("PDF", result.getDetectedType());
    }

    // thread pool: a batch of KYC documents is checked in parallel on the doc-worker pool
    @Test
    void kycBatchIsCheckedOnThePool() throws Exception {
        long client = people.newClient();
        String firstPath = fileStorageService.store(newPdf(), "kyc_documents");
        String secondPath = fileStorageService.store(newPdf(), "kyc_documents");
        Long first = kycDocumentRepository.save(newKycDocument(client, DocumentType.NATIONAL_ID, KycStatus.PENDING, firstPath)).getId();
        Long second = kycDocumentRepository.save(newKycDocument(client, DocumentType.PASSPORT, KycStatus.PENDING, secondPath)).getId();

        documentProcessor.processKycAll(client, List.of(first, second)).get(30, TimeUnit.SECONDS);

        for (KycDocument doc : kycDocumentRepository.findAllById(List.of(first, second))) {
            assertEquals("PDF", doc.getDetectedType(), "document " + doc.getId());
            assertNotNull(doc.getSha256());
            assertNull(doc.getFlagReason());
        }
    }

    // the same file uploaded by two different accounts: the second one is flagged
    @Test
    void sameFileFromAnotherAccountIsFlagged() throws Exception {
        String path = fileStorageService.store(newPdf(), "kyc_documents");
        long owner = people.newClient();
        long copycat = people.newClient();
        Long ownerDoc = kycDocumentRepository.save(newKycDocument(owner, DocumentType.NATIONAL_ID, KycStatus.PENDING, path)).getId();
        Long copyDoc = kycDocumentRepository.save(newKycDocument(copycat, DocumentType.NATIONAL_ID, KycStatus.PENDING, path)).getId();

        documentProcessor.processKycAll(owner, List.of(ownerDoc)).get(30, TimeUnit.SECONDS);
        documentProcessor.processKycAll(copycat, List.of(copyDoc)).get(30, TimeUnit.SECONDS);

        assertNull(kycDocumentRepository.findById(ownerDoc).orElseThrow().getFlagReason(), "the first upload is clean");
        assertEquals("Same file was uploaded by another account",
                kycDocumentRepository.findById(copyDoc).orElseThrow().getFlagReason());
        assertTrue(Files.exists(UPLOADS.resolve("kyc_documents")), "the files went into the temp dir");
    }
}
