package com.example.documentservice.service;

import com.example.documentservice.integration.AdminNotifier;
import com.example.documentservice.integration.CheckEventPublisher;
import com.example.documentservice.model.AdvisorApplication;
import com.example.documentservice.model.ApplicationCertificate;
import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.CertificateStatus;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.NotificationType;
import com.example.documentservice.model.enums.Specialty;
import com.example.documentservice.repository.AdvisorApplicationRepository;
import com.example.documentservice.repository.ApplicationCertificateRepository;
import com.example.documentservice.repository.KycDocumentRepository;
import com.example.documentservice.support.TestPeople;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// runs on finconsult_test (TEST_DB_URL), no @Transactional on the class, so the repositories, the unique index and the
// version checks are the real ones. Uploaded files go into a temp dir that is deleted afterwards, VirusTotal and the two
// stubs are mocks, and the users are made by TestPeople and deleted with everything that belongs to them
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class DocumentProcessorTest {

    private static final Path UPLOADS = tempDir();

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] DOCX = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] NOT_A_DOCUMENT = "just some text".getBytes(StandardCharsets.UTF_8);

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("document-processor-test");
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
    @MockitoBean CheckEventPublisher checkEvents;
    @MockitoBean AdminNotifier adminNotifier;

    private TestPeople people;
    private long client;
    private long otherClient;
    private long admin;

    @BeforeEach
    void setUp() {
        people = new TestPeople(jdbcTemplate);
        client = people.newClient();
        otherClient = people.newClient();
        admin = people.newAdmin();
        when(virusTotalService.lookup(anyString())).thenReturn(ScanResult.clean());
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        people.deleteCreated();
    }

    // helpers

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

    private KycDocument document(long userId, DocumentType type, KycStatus status, byte[] bytes) {
        KycDocument doc = new KycDocument();
        doc.setUserId(userId);
        doc.setDocumentType(type);
        doc.setFilePath(store(bytes, "kyc_documents"));
        doc.setOriginalFileName("upload.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(bytes.length);
        doc.setStatus(status);
        return kycDocumentRepository.save(doc);
    }

    private KycDocument checked(KycDocument doc) throws Exception {
        processor.processKycAll(doc.getUserId(), List.of(doc.getId())).get(30, TimeUnit.SECONDS);
        return kycDocumentRepository.findById(doc.getId()).orElseThrow();
    }

    private AdvisorApplication application(long userId) {
        AdvisorApplication application = new AdvisorApplication();
        application.setUserId(userId);
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

    private CertificateStatus statusOf(ApplicationCertificate certificate) {
        return certificateRepository.findById(certificate.getId()).orElseThrow().getStatus();
    }

    // magic bytes

    @Test
    void theRealTypeIsReadFromTheFirstBytes() {
        assertEquals("PDF", processor.detectType(file(PDF)));
        assertEquals("PNG", processor.detectType(file(PNG)));
        assertEquals("JPG", processor.detectType(file(JPG)));
        assertEquals("DOCX", processor.detectType(file(DOCX)));
        assertNull(processor.detectType(NOT_A_DOCUMENT));
        assertNull(processor.detectType(new byte[]{0x25, 0x50}), "too short to match");
        assertNull(processor.detectType(new byte[0]));
    }

    @Test
    void aGenuineDocumentGetsItsTypeAndHashAndNoFlag() throws Exception {
        byte[] bytes = file(PNG);
        KycDocument result = checked(document(client, DocumentType.PASSPORT, KycStatus.PENDING, bytes));

        assertEquals("PNG", result.getDetectedType());
        assertNotNull(result.getSha256());
        assertEquals(64, result.getSha256().length());
        assertNull(result.getFlagReason());
        assertEquals(KycStatus.PENDING, result.getStatus());
        verify(virusTotalService).lookup(result.getSha256());
    }

    @Test
    void aFileThatIsNotARealDocumentIsFlagged() throws Exception {
        KycDocument result = checked(document(client, DocumentType.PASSPORT, KycStatus.PENDING, NOT_A_DOCUMENT));

        assertNull(result.getDetectedType());
        assertEquals("File content is not a real PDF, JPG, PNG or DOCX", result.getFlagReason());
    }

    @Test
    void aFileThatCannotBeReadIsFlagged() throws Exception {
        KycDocument doc = document(client, DocumentType.PASSPORT, KycStatus.PENDING, file(PDF));
        doc.setFilePath("/uploads/kyc_documents/gone.pdf");
        doc = kycDocumentRepository.save(doc);

        assertEquals("Could not read the file", checked(doc).getFlagReason());
    }

    // duplicates and malware

    @Test
    void theSameFileFromAnotherAccountIsFlagged() throws Exception {
        byte[] bytes = file(PDF);
        KycDocument first = checked(document(client, DocumentType.NATIONAL_ID, KycStatus.PENDING, bytes));
        KycDocument second = checked(document(otherClient, DocumentType.NATIONAL_ID, KycStatus.PENDING, bytes));

        assertNull(first.getFlagReason(), "nobody else had it when it was checked");
        assertEquals("Same file was uploaded by another account", second.getFlagReason());
    }

    @Test
    void theSameFileTwiceFromOneAccountIsNotADuplicate() throws Exception {
        byte[] bytes = file(PDF);
        checked(document(client, DocumentType.NATIONAL_ID, KycStatus.PENDING, bytes));

        assertNull(checked(document(client, DocumentType.PASSPORT, KycStatus.PENDING, bytes)).getFlagReason());
    }

    @Test
    void aFileVirusTotalCallsMaliciousIsFlagged() throws Exception {
        KycDocument doc = document(client, DocumentType.PASSPORT, KycStatus.PENDING, file(PDF));
        when(virusTotalService.lookup(anyString())).thenReturn(ScanResult.malicious(5));

        assertEquals("Matches malware", checked(doc).getFlagReason());
    }

    @Test
    void aSkippedScanCountsAsClean() throws Exception {
        when(virusTotalService.lookup(anyString())).thenReturn(ScanResult.skipped("rate limit"));

        assertNull(checked(document(client, DocumentType.PASSPORT, KycStatus.PENDING, file(PDF))).getFlagReason());
    }

    // the stubs

    @Test
    void whenTheChecksAreDoneTheUserGetsAnEventAndFlaggedFilesTellTheAdmins() throws Exception {
        KycDocument clean = document(client, DocumentType.NATIONAL_ID, KycStatus.PENDING, file(PDF));
        KycDocument fake = document(client, DocumentType.PASSPORT, KycStatus.PENDING, NOT_A_DOCUMENT);

        processor.processKycAll(client, List.of(clean.getId(), fake.getId())).get(30, TimeUnit.SECONDS);

        verify(checkEvents).push(client, Map.of("type", "KYC_CHECKED", "documentIds", List.of(clean.getId(), fake.getId())));
        verify(adminNotifier).notifyAdmins(eq(NotificationType.DOCUMENT_FLAGGED), contains("1 KYC document(s) from user " + client));
    }

    @Test
    void cleanDocumentsTellTheUserButNotTheAdmins() throws Exception {
        checked(document(client, DocumentType.PASSPORT, KycStatus.PENDING, file(JPG)));

        verify(checkEvents).push(anyLong(), eq(Map.of("type", "KYC_CHECKED", "documentIds",
                List.of(kycDocumentRepository.findByUserId(client).get(0).getId()))));
        verify(adminNotifier, never()).notifyAdmins(eq(NotificationType.DOCUMENT_FLAGGED), anyString());
    }

    // saving the result while an admin reviews at the same time

    @Test
    void aReviewDuringTheCheckIsRetriedAndKeptWhenTheFileIsClean() {
        KycDocument saved = document(client, DocumentType.PASSPORT, KycStatus.PENDING, file(PDF));
        KycDocument staleCopy = kycDocumentRepository.findById(saved.getId()).orElseThrow(); // what the worker loaded
        approve(saved.getId());                                                               // the admin approves meanwhile

        staleCopy.setDetectedType("PDF");
        staleCopy.setSha256("abc");
        KycDocument result = processor.saveCheckResult(saved.getId(), staleCopy);

        KycDocument stored = kycDocumentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(KycStatus.APPROVED, stored.getStatus(), "the stale copy did not overwrite the approval");
        assertEquals(admin, stored.getReviewedBy());
        assertEquals("PDF", stored.getDetectedType(), "the check result still got saved on the retry");
        assertEquals(stored.getVersion(), result.getVersion());
    }

    @Test
    void aDocumentApprovedDuringTheCheckGoesBackToPendingWhenFlagged() {
        KycDocument saved = document(client, DocumentType.PASSPORT, KycStatus.PENDING, file(PDF));
        KycDocument staleCopy = kycDocumentRepository.findById(saved.getId()).orElseThrow();
        approve(saved.getId());

        staleCopy.setFlagReason("Matches malware");
        processor.saveCheckResult(saved.getId(), staleCopy);

        KycDocument stored = kycDocumentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(KycStatus.PENDING, stored.getStatus(), "approved but flagged: back to review");
        assertNull(stored.getReviewedBy());
        assertNull(stored.getReviewedAt());
        assertEquals("Matches malware", stored.getFlagReason());
    }

    @Test
    void aFlaggedApprovalStaysApprovedWhenAnotherPendingDocumentOfTheTypeExists() {
        KycDocument saved = document(client, DocumentType.PASSPORT, KycStatus.PENDING, file(PDF));
        KycDocument staleCopy = kycDocumentRepository.findById(saved.getId()).orElseThrow();
        approve(saved.getId());
        document(client, DocumentType.PASSPORT, KycStatus.PENDING, file(PDF)); // a new pending passport, allowed now

        staleCopy.setFlagReason("Matches malware");
        processor.saveCheckResult(saved.getId(), staleCopy);

        KycDocument stored = kycDocumentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(KycStatus.APPROVED, stored.getStatus(), "going back to pending would break the one-pending index");
        assertEquals("Matches malware", stored.getFlagReason(), "the flag is still recorded");
    }

    private void approve(long documentId) {
        KycDocument doc = kycDocumentRepository.findById(documentId).orElseThrow();
        doc.setStatus(KycStatus.APPROVED);
        doc.setReviewedBy(admin);
        doc.setReviewedAt(LocalDateTime.now());
        kycDocumentRepository.save(doc);
    }

    // certificates

    @Test
    void genuineCertificatesAreValidAndFakeOrMaliciousOnesAreFlagged() throws Exception {
        AdvisorApplication application = application(client);
        ApplicationCertificate valid = certificate(application, file(PDF));
        ApplicationCertificate fake = certificate(application, NOT_A_DOCUMENT);
        byte[] malware = file(PNG);
        ApplicationCertificate malicious = certificate(application, malware);
        String malwareHash = sha256(malware);
        when(virusTotalService.lookup(malwareHash)).thenReturn(ScanResult.malicious(2));

        processor.processAll(application.getId(), client, List.of(valid.getId(), fake.getId(), malicious.getId()))
                .get(30, TimeUnit.SECONDS);

        assertEquals(CertificateStatus.VALID, statusOf(valid));
        assertEquals("PDF", certificateRepository.findById(valid.getId()).orElseThrow().getDetectedType());
        assertEquals(CertificateStatus.FLAGGED, statusOf(fake));
        assertEquals(CertificateStatus.FLAGGED, statusOf(malicious));
        verify(checkEvents).push(client, Map.of("type", "CERTIFICATES_CHECKED", "applicationId", application.getId()));
        verify(adminNotifier).notifyAdmins(eq(NotificationType.DOCUMENT_FLAGGED), contains("2 certificate(s)"));
    }

    @Test
    void aCertificateAnotherApplicantAlreadyUploadedIsFlagged() throws Exception {
        byte[] bytes = file(PDF);
        AdvisorApplication first = application(otherClient);
        ApplicationCertificate original = certificate(first, bytes);
        processor.processAll(first.getId(), otherClient, List.of(original.getId())).get(30, TimeUnit.SECONDS);

        AdvisorApplication second = application(client);
        ApplicationCertificate copy = certificate(second, bytes);
        processor.processAll(second.getId(), client, List.of(copy.getId())).get(30, TimeUnit.SECONDS);

        assertEquals(CertificateStatus.VALID, statusOf(original));
        assertEquals(CertificateStatus.FLAGGED, statusOf(copy));
    }

    @Test
    void theSameCertificateTwiceInOneApplicationIsFlagged() throws Exception {
        byte[] bytes = file(PDF);
        AdvisorApplication application = application(client);
        ApplicationCertificate first = certificate(application, bytes);
        processor.processAll(application.getId(), client, List.of(first.getId())).get(30, TimeUnit.SECONDS);
        ApplicationCertificate again = certificate(application, bytes);

        processor.processAll(application.getId(), client, List.of(again.getId())).get(30, TimeUnit.SECONDS);

        assertEquals(CertificateStatus.FLAGGED, statusOf(again));
    }

    @Test
    void cleanCertificatesTellTheApplicantButNotTheAdmins() throws Exception {
        AdvisorApplication application = application(client);
        ApplicationCertificate valid = certificate(application, file(JPG));

        processor.processAll(application.getId(), client, List.of(valid.getId())).get(30, TimeUnit.SECONDS);

        assertEquals(CertificateStatus.VALID, statusOf(valid));
        verify(checkEvents).push(client, Map.of("type", "CERTIFICATES_CHECKED", "applicationId", application.getId()));
        verify(adminNotifier, never()).notifyAdmins(eq(NotificationType.DOCUMENT_FLAGGED), anyString());
        assertTrue(Files.exists(UPLOADS.resolve("advisor_applications")), "the files went into the temp dir");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
