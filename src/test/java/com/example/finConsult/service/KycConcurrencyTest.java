package com.example.finConsult.service;

import com.example.finConsult.model.KycDocument;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.KycStatus;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// KYC documents: double upload, the background check vs an admin review, and the pool
class KycConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired KycService kycService;
    @Autowired DocumentProcessor documentProcessor;

    // one client uploads the same document type twice at once: only one pending document may exist
    @RepeatedTest(10)
    void doubleUploadCreatesOnePendingDocument() throws Exception {
        User client = newClient();

        Result result = runAtOnce(2, () -> kycService.submit(client, newPdf(), DocumentType.NATIONAL_ID));

        long pending = kycDocumentRepository.findByUser(client).stream()
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
        User client = newClient();
        KycDocument saved = kycDocumentRepository.save(newKycDocument(client, DocumentType.PASSPORT, KycStatus.PENDING, "/uploads/none.pdf"));
        Long id = saved.getId();

        KycDocument staleCopy = kycDocumentRepository.findById(id).orElseThrow();   // what the worker loaded
        kycService.approve(id, admin());                                             // the admin approves meanwhile

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

    // same race, but the check found nothing: the approval stays and the type/hash are added
    @Test
    void cleanDocumentApprovedDuringTheCheckStaysApproved() {
        User client = newClient();
        KycDocument saved = kycDocumentRepository.save(newKycDocument(client, DocumentType.PASSPORT, KycStatus.PENDING, "/uploads/none.pdf"));
        Long id = saved.getId();

        KycDocument staleCopy = kycDocumentRepository.findById(id).orElseThrow();
        kycService.approve(id, admin());

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
        User client = newClient();
        String firstPath = fileStorageService.store(newPdf(), "kyc_documents");
        String secondPath = fileStorageService.store(newPdf(), "kyc_documents");
        Long first = kycDocumentRepository.save(newKycDocument(client, DocumentType.NATIONAL_ID, KycStatus.PENDING, firstPath)).getId();
        Long second = kycDocumentRepository.save(newKycDocument(client, DocumentType.PASSPORT, KycStatus.PENDING, secondPath)).getId();

        documentProcessor.processKycAll(client.getId(), List.of(first, second)).get(30, TimeUnit.SECONDS);

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
        User owner = newClient();
        User copycat = newClient();
        Long ownerDoc = kycDocumentRepository.save(newKycDocument(owner, DocumentType.NATIONAL_ID, KycStatus.PENDING, path)).getId();
        Long copyDoc = kycDocumentRepository.save(newKycDocument(copycat, DocumentType.NATIONAL_ID, KycStatus.PENDING, path)).getId();

        documentProcessor.processKycAll(owner.getId(), List.of(ownerDoc)).get(30, TimeUnit.SECONDS);
        documentProcessor.processKycAll(copycat.getId(), List.of(copyDoc)).get(30, TimeUnit.SECONDS);

        assertNull(kycDocumentRepository.findById(ownerDoc).orElseThrow().getFlagReason(), "the first upload is clean");
        assertEquals("Same file was uploaded by another account",
                kycDocumentRepository.findById(copyDoc).orElseThrow().getFlagReason());
    }
}
