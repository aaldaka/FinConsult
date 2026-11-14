package com.example.documentservice.service;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.exception.InvalidRequest;
import com.example.documentservice.integration.AdminNotifier;
import com.example.documentservice.integration.AuditRecorder;
import com.example.documentservice.integration.CheckEventPublisher;
import com.example.documentservice.integration.NotificationPublisher;
import com.example.documentservice.model.AdvisorApplication;
import com.example.documentservice.model.ApplicationCertificate;
import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.CertificateStatus;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.Specialty;
import com.example.documentservice.model.request.AdvisorApplicationRequest;
import com.example.documentservice.repository.AdvisorApplicationRepository;
import com.example.documentservice.repository.ApplicationCertificateRepository;
import com.example.documentservice.repository.KycDocumentRepository;
import com.example.documentservice.support.FakeUserService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// the main app's ApplicationConcurrencyTest, ported. Runs on finconsult_test (TEST_DB_URL), no @Transactional on the
// class, so the unique index, the version checks and the force-increment lock are the real ones. Advisors live in the
// user service now, so FakeUserService plays it: "one Advisor row" became "promoted exactly once". Each test makes its
// own users with TestPeople and deletes them with their applications afterwards, and files go into a temp dir
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class ApplicationConcurrencyTest {

    private static final Path UPLOADS = tempDir();

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("application-concurrency-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void deleteTheUploads() throws IOException {
        FileSystemUtils.deleteRecursively(UPLOADS);
    }

    @Autowired AdvisorApplicationService applicationService;
    @Autowired DocumentProcessor documentProcessor;
    @Autowired FileStorageService fileStorageService;
    @Autowired AdvisorApplicationRepository applicationRepository;
    @Autowired ApplicationCertificateRepository certificateRepository;
    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean UserClient userClient;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean CheckEventPublisher checkEvents;
    @MockitoBean AdminNotifier adminNotifier;
    @MockitoBean VirusTotalService virusTotalService;

    private TestPeople people;
    private FakeUserService users;
    private long admin;

    @BeforeEach
    void setUp() {
        people = new TestPeople(jdbcTemplate);
        users = new FakeUserService(userClient, () -> {});
        admin = people.newAdmin();
        when(virusTotalService.lookup(anyString())).thenReturn(ScanResult.clean());
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        people.deleteCreated();
    }

    // helpers

    private MockMultipartFile newPdf() {
        return new MockMultipartFile("file", "cert.pdf", "application/pdf",
                ("%PDF-1.4 " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    }

    private void giveApprovedIdentity(long userId) {
        KycDocument doc = new KycDocument();
        doc.setUserId(userId);
        doc.setDocumentType(DocumentType.NATIONAL_ID);
        doc.setFilePath("/uploads/none.pdf");
        doc.setOriginalFileName("id.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(10);
        doc.setStatus(KycStatus.APPROVED);
        kycDocumentRepository.save(doc);
    }

    private AdvisorApplication newPendingApplication(long userId) {
        AdvisorApplication application = new AdvisorApplication();
        application.setUserId(userId);
        application.setTitle("Test advisor");
        application.setSpecialty(Specialty.GENERAL);
        application.setYearsOfExperience(3);
        application.setStatus(KycStatus.PENDING);
        return applicationRepository.save(application);
    }

    private ApplicationCertificate newCertificate(AdvisorApplication application, CertificateStatus status, String path) {
        ApplicationCertificate certificate = new ApplicationCertificate();
        certificate.setApplication(application);
        certificate.setFilePath(path);
        certificate.setOriginalFileName("cert.pdf");
        certificate.setContentType("application/pdf");
        certificate.setFileSize(10);
        certificate.setStatus(status);
        return certificateRepository.save(certificate);
    }

    // submit starts the certificate checks; wait for them so the cleanup does not race them
    private void awaitChecksOf(long userId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            boolean running = applicationRepository.findByUserId(userId).stream()
                    .flatMap(a -> certificateRepository.findByApplication(a).stream())
                    .anyMatch(c -> c.getStatus() == CertificateStatus.PROCESSING);
            if (!running) {
                return;
            }
            Thread.sleep(100);
        }
    }

    // one client presses Submit twice at once: only one pending application may exist
    @RepeatedTest(10)
    void doubleSubmitCreatesOnePendingApplication() throws Exception {
        long applicant = people.newClient();
        giveApprovedIdentity(applicant);

        AdvisorApplicationRequest request = new AdvisorApplicationRequest();
        request.setTitle("Test advisor");
        request.setSpecialty(Specialty.GENERAL);
        request.setYearsOfExperience(3);

        RaceRunner.Result result = RaceRunner.runAtOnce(2, () -> applicationService.submit(applicant, request, List.of(newPdf())));
        awaitChecksOf(applicant);

        long pending = applicationRepository.findByUserId(applicant).stream()
                .filter(a -> a.getStatus() == KycStatus.PENDING)
                .count();

        assertEquals(0, result.other());
        assertEquals(1, result.successes(), "exactly one submit should win");
        assertEquals(1, result.refused(), "the other one should be refused");
        assertEquals(1, pending, "exactly one pending application in the database");
    }

    // two admins approve the same application at once
    @RepeatedTest(10)
    void twoAdminsApproveOneApplication() throws Exception {
        long applicant = people.newClient();
        AdvisorApplication application = newPendingApplication(applicant);

        RaceRunner.Result result = RaceRunner.runAtOnce(2, () -> applicationService.approve(application.getId(), admin));

        assertEquals(0, result.other());
        assertEquals(1, result.successes(), "exactly one approval should win");
        assertEquals(1, result.refused());
        assertEquals(1, users.promotions(), "promoted exactly once");
        assertTrue(users.isPromoted(applicant));
        assertEquals(KycStatus.APPROVED, applicationRepository.findById(application.getId()).orElseThrow().getStatus());
    }

    // one admin approves while another rejects: one decision wins. If the approval wins the user is promoted. If the
    // rejection wins after the promotion was made, the user stays promoted: that is the known gap noted in approve()
    @RepeatedTest(10)
    void approveAndRejectAtOnce() throws Exception {
        long applicant = people.newClient();
        AdvisorApplication application = newPendingApplication(applicant);

        RaceRunner.Result result = RaceRunner.runAtOnce(List.of(
                () -> applicationService.approve(application.getId(), admin),
                () -> applicationService.reject(application.getId(), "not enough experience", admin)));

        KycStatus status = applicationRepository.findById(application.getId()).orElseThrow().getStatus();

        assertEquals(0, result.other());
        assertEquals(1, result.successes());
        assertEquals(1, result.refused());
        if (status == KycStatus.APPROVED) {
            assertTrue(users.isPromoted(applicant), "approved -> the user is an advisor");
        } else {
            assertEquals(KycStatus.REJECTED, status);
        }
        assertTrue(users.promotions() <= 1);
    }

    // approving is blocked while the certificates are still being checked, allowed after
    @Test
    void cannotApproveWhileCertificatesAreProcessing() {
        long applicant = people.newClient();
        AdvisorApplication application = newPendingApplication(applicant);
        ApplicationCertificate certificate = newCertificate(application, CertificateStatus.PROCESSING, "/uploads/none.pdf");

        assertThrows(InvalidRequest.class, () -> applicationService.approve(application.getId(), admin));
        assertFalse(users.isPromoted(applicant));

        certificate.setStatus(CertificateStatus.VALID);
        certificateRepository.save(certificate);

        applicationService.approve(application.getId(), admin);
        assertTrue(users.isPromoted(applicant));
    }

    // thread pool: three certificates are checked in parallel on the doc-worker pool and all get a result
    @Test
    void threeCertificatesAreCheckedOnThePool() throws Exception {
        long applicant = people.newClient();
        AdvisorApplication application = newPendingApplication(applicant);

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String path = fileStorageService.store(newPdf(), "advisor_applications");
            ids.add(newCertificate(application, CertificateStatus.PROCESSING, path).getId());
        }

        documentProcessor.processAll(application.getId(), applicant, ids).get(30, TimeUnit.SECONDS);

        List<ApplicationCertificate> checked = certificateRepository.findAllById(ids);
        assertEquals(3, checked.size());
        for (ApplicationCertificate certificate : checked) {
            assertEquals(CertificateStatus.VALID, certificate.getStatus(), "certificate " + certificate.getId());
            assertEquals("PDF", certificate.getDetectedType());
        }
    }
}
