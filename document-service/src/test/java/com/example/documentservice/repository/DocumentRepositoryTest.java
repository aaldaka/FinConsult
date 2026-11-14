package com.example.documentservice.repository;

import com.example.documentservice.model.AdvisorApplication;
import com.example.documentservice.model.ApplicationCertificate;
import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.CertificateStatus;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.Specialty;
import com.example.documentservice.support.TestPeople;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// runs on finconsult_test (TEST_DB_URL), no @Transactional on the class, so every save is a real commit and the
// database's own indexes and version checks are what is tested. The users are made by TestPeople and deleted afterwards
// with everything that belongs to them
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class DocumentRepositoryTest {

    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired AdvisorApplicationRepository applicationRepository;
    @Autowired ApplicationCertificateRepository certificateRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

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
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        people.deleteCreated();
    }

    // helpers

    private KycDocument document(long userId, DocumentType type, KycStatus status) {
        KycDocument doc = new KycDocument();
        doc.setUserId(userId);
        doc.setDocumentType(type);
        doc.setFilePath("/uploads/kyc_documents/none.pdf");
        doc.setOriginalFileName("id.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(1024);
        doc.setStatus(status);
        return doc;
    }

    private AdvisorApplication application(long userId, KycStatus status) {
        AdvisorApplication application = new AdvisorApplication();
        application.setUserId(userId);
        application.setTitle("Planner");
        application.setSpecialty(Specialty.GENERAL);
        application.setYearsOfExperience(3);
        application.setStatus(status);
        return application;
    }

    private ApplicationCertificate certificate(AdvisorApplication application, String sha256) {
        ApplicationCertificate certificate = new ApplicationCertificate();
        certificate.setApplication(application);
        certificate.setFilePath("/uploads/advisor_applications/none.pdf");
        certificate.setOriginalFileName("cfa.pdf");
        certificate.setContentType("application/pdf");
        certificate.setFileSize(2048);
        certificate.setStatus(CertificateStatus.VALID);
        certificate.setSha256(sha256);
        return certificate;
    }

    // the entities map the existing columns

    @Test
    void aDocumentKeepsItsUserReviewerAndCheckFields() {
        KycDocument doc = document(client, DocumentType.PASSPORT, KycStatus.APPROVED);
        doc.setReviewedBy(admin);
        doc.setReviewedAt(LocalDateTime.now());
        doc.setDetectedType("PDF");
        doc.setSha256("abc123");
        doc.setFlagReason("Matches malware");
        long id = kycDocumentRepository.save(doc).getId();

        KycDocument loaded = kycDocumentRepository.findById(id).orElseThrow();

        assertEquals(client, loaded.getUserId());
        assertEquals(admin, loaded.getReviewedBy());
        assertEquals(DocumentType.PASSPORT, loaded.getDocumentType());
        assertEquals("abc123", loaded.getSha256());
        assertEquals("Matches malware", loaded.getFlagReason());
        assertEquals(0L, loaded.getVersion());
        assertTrue(loaded.getCreatedAt() != null && loaded.getUpdatedAt() != null);
    }

    @Test
    void aCertificateKeepsItsApplication() {
        AdvisorApplication application = applicationRepository.save(application(client, KycStatus.PENDING));
        long id = certificateRepository.save(certificate(application, "hash-1")).getId();

        ApplicationCertificate loaded = certificateRepository.findById(id).orElseThrow();

        assertEquals(application.getId(), loaded.getApplication().getId());
        assertEquals(client, loaded.getApplication().getUserId());
        assertEquals(1, certificateRepository.findByApplication(application).size());
    }

    // the queries, rewritten for user ids

    @Test
    void theDocumentQueriesWorkOnUserIds() {
        kycDocumentRepository.save(document(client, DocumentType.NATIONAL_ID, KycStatus.APPROVED));
        KycDocument pending = document(client, DocumentType.PROOF_OF_ADDRESS, KycStatus.PENDING);
        pending.setSha256("shared-hash");
        kycDocumentRepository.save(pending);

        assertTrue(kycDocumentRepository.existsByUserIdAndStatus(client, KycStatus.APPROVED));
        assertFalse(kycDocumentRepository.existsByUserIdAndStatus(otherClient, KycStatus.APPROVED));
        assertTrue(kycDocumentRepository.existsByUserIdAndDocumentTypeAndStatus(client, DocumentType.NATIONAL_ID, KycStatus.APPROVED));
        assertFalse(kycDocumentRepository.existsByUserIdAndDocumentTypeAndStatus(client, DocumentType.PASSPORT, KycStatus.APPROVED));
        assertEquals(2, kycDocumentRepository.findByUserId(client).size());
        assertTrue(kycDocumentRepository.existsBySha256AndUserIdNot("shared-hash", otherClient), "another account has this file");
        assertFalse(kycDocumentRepository.existsBySha256AndUserIdNot("shared-hash", client), "only the owner has it");
    }

    @Test
    void theApplicationAndCertificateQueriesWorkOnUserIds() {
        AdvisorApplication rejected = applicationRepository.save(application(client, KycStatus.REJECTED));
        AdvisorApplication pending = applicationRepository.save(application(client, KycStatus.PENDING));
        ApplicationCertificate first = certificateRepository.save(certificate(pending, "cert-hash"));
        certificateRepository.save(certificate(rejected, "other-hash"));

        assertEquals(2, applicationRepository.countByUserId(client));
        assertEquals(2, applicationRepository.findByUserId(client).size());
        assertTrue(applicationRepository.existsByUserIdAndStatus(client, KycStatus.PENDING));
        assertFalse(applicationRepository.existsByUserIdAndStatus(otherClient, KycStatus.PENDING));
        assertTrue(certificateRepository.existsBySha256AndApplication_UserIdNot("cert-hash", otherClient));
        assertFalse(certificateRepository.existsBySha256AndApplication_UserIdNot("cert-hash", client));
        assertFalse(certificateRepository.existsBySha256AndApplicationAndIdNot("cert-hash", pending, first.getId()), "only itself");
        certificateRepository.save(certificate(pending, "cert-hash"));
        assertTrue(certificateRepository.existsBySha256AndApplicationAndIdNot("cert-hash", pending, first.getId()), "twice in one application");
    }

    // the partial unique indexes

    @Test
    void aSecondPendingDocumentOfTheSameTypeIsRejectedByTheIndex() {
        kycDocumentRepository.save(document(client, DocumentType.NATIONAL_ID, KycStatus.PENDING));

        assertThrows(DataIntegrityViolationException.class,
                () -> kycDocumentRepository.save(document(client, DocumentType.NATIONAL_ID, KycStatus.PENDING)));
    }

    @Test
    void theDocumentIndexOnlyCoversPendingRowsOfOneUserAndType() {
        kycDocumentRepository.save(document(client, DocumentType.NATIONAL_ID, KycStatus.PENDING));

        assertDoesNotThrow(() -> kycDocumentRepository.save(document(client, DocumentType.PASSPORT, KycStatus.PENDING)));
        assertDoesNotThrow(() -> kycDocumentRepository.save(document(otherClient, DocumentType.NATIONAL_ID, KycStatus.PENDING)));
        assertDoesNotThrow(() -> kycDocumentRepository.save(document(client, DocumentType.NATIONAL_ID, KycStatus.REJECTED)));
        assertDoesNotThrow(() -> kycDocumentRepository.save(document(client, DocumentType.NATIONAL_ID, KycStatus.APPROVED)));
    }

    @Test
    void aSecondPendingApplicationIsRejectedByTheIndex() {
        applicationRepository.save(application(client, KycStatus.PENDING));

        assertThrows(DataIntegrityViolationException.class,
                () -> applicationRepository.save(application(client, KycStatus.PENDING)));
    }

    @Test
    void theApplicationIndexOnlyCoversPendingRowsOfOneUser() {
        applicationRepository.save(application(client, KycStatus.PENDING));

        assertDoesNotThrow(() -> applicationRepository.save(application(client, KycStatus.REJECTED)));
        assertDoesNotThrow(() -> applicationRepository.save(application(otherClient, KycStatus.PENDING)));
    }

    // @Version

    @Test
    void twoReviewsOfTheSameDocumentConflict() {
        long id = kycDocumentRepository.save(document(client, DocumentType.PASSPORT, KycStatus.PENDING)).getId();
        KycDocument first = kycDocumentRepository.findById(id).orElseThrow();
        KycDocument second = kycDocumentRepository.findById(id).orElseThrow();

        first.setStatus(KycStatus.APPROVED);
        kycDocumentRepository.save(first);
        second.setStatus(KycStatus.REJECTED);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> kycDocumentRepository.save(second));
        assertEquals(KycStatus.APPROVED, kycDocumentRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void twoReviewsOfTheSameApplicationConflict() {
        long id = applicationRepository.save(application(client, KycStatus.PENDING)).getId();
        AdvisorApplication first = applicationRepository.findById(id).orElseThrow();
        AdvisorApplication second = applicationRepository.findById(id).orElseThrow();

        first.setStatus(KycStatus.APPROVED);
        applicationRepository.save(first);
        second.setStatus(KycStatus.REJECTED);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> applicationRepository.save(second));
        assertEquals(KycStatus.APPROVED, applicationRepository.findById(id).orElseThrow().getStatus());
    }

    // findLockedById

    @Test
    void findLockedByIdBumpsTheVersionEvenWithoutAChange() {
        long id = applicationRepository.save(application(client, KycStatus.PENDING)).getId();
        long before = applicationRepository.findById(id).orElseThrow().getVersion();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                applicationRepository.findLockedById(id).orElseThrow());

        assertEquals(before + 1, applicationRepository.findById(id).orElseThrow().getVersion());
    }

    @Test
    void aReviewThatReadTheApplicationBeforeALockedReadConflicts() {
        long id = applicationRepository.save(application(client, KycStatus.PENDING)).getId();
        AdvisorApplication review = applicationRepository.findById(id).orElseThrow(); // an admin opens it

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                applicationRepository.findLockedById(id).orElseThrow()); // a certificate is replaced meanwhile

        review.setStatus(KycStatus.APPROVED);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> applicationRepository.save(review));
    }
}
