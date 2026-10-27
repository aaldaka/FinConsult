package com.example.finConsult.service;

import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.AdvisorApplication;
import com.example.finConsult.model.ApplicationCertificate;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.CertificateStatus;
import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.enums.Specialty;
import com.example.finConsult.model.enums.UserRole;
import com.example.finConsult.model.request.AdvisorApplicationRequest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Advisor application flow: double submit, two admins, approve vs reject, certificate checks on the pool
class ApplicationConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired AdvisorApplicationService applicationService;
    @Autowired DocumentProcessor documentProcessor;

    private long advisorRows(User applicant) {
        return advisorRepository.findAll().stream()
                .filter(a -> a.getUser().getId().equals(applicant.getId()))
                .count();
    }

    // one client presses Submit twice at once: only one pending application may exist
    @RepeatedTest(10)
    void doubleSubmitCreatesOnePendingApplication() throws Exception {
        User applicant = newClient();
        giveApprovedIdentity(applicant);

        AdvisorApplicationRequest request = new AdvisorApplicationRequest();
        request.setTitle("Test advisor");
        request.setSpecialty(Specialty.GENERAL);
        request.setYearsOfExperience(3);

        Result result = runAtOnce(2, () -> applicationService.submit(applicant, request, List.of(newPdf())));

        long pending = applicationRepository.findByUser(applicant).stream()
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
        User applicant = newClient();
        AdvisorApplication application = newPendingApplication(applicant);
        User admin = admin();

        Result result = runAtOnce(2, () -> applicationService.approve(application.getId(), admin));

        assertEquals(0, result.other());
        assertEquals(1, result.successes(), "exactly one approval should win");
        assertEquals(1, result.refused());
        assertEquals(1, advisorRows(applicant), "exactly one Advisor row");
        assertEquals(UserRole.ADVISOR, userRepository.findById(applicant.getId()).orElseThrow().getRole());
    }

    // one admin approves while another rejects: one decision wins and the result is consistent
    @RepeatedTest(10)
    void approveAndRejectAtOnce() throws Exception {
        User applicant = newClient();
        AdvisorApplication application = newPendingApplication(applicant);
        User admin = admin();

        Result result = runAtOnce(List.of(
                () -> applicationService.approve(application.getId(), admin),
                () -> applicationService.reject(application.getId(), "not enough experience", admin)));

        KycStatus status = applicationRepository.findById(application.getId()).orElseThrow().getStatus();

        assertEquals(0, result.other());
        assertEquals(1, result.successes());
        assertEquals(1, result.refused());
        // approved -> the user is an advisor; rejected -> no advisor row
        assertEquals(status == KycStatus.APPROVED ? 1 : 0, advisorRows(applicant));
    }

    // approving is blocked while the certificates are still being checked, allowed after
    @Test
    void cannotApproveWhileCertificatesAreProcessing() {
        User applicant = newClient();
        AdvisorApplication application = newPendingApplication(applicant);
        ApplicationCertificate certificate = newCertificate(application, CertificateStatus.PROCESSING, "/uploads/none.pdf");
        User admin = admin();

        assertThrows(InvalidRequest.class, () -> applicationService.approve(application.getId(), admin));
        assertEquals(0, advisorRows(applicant));

        certificate.setStatus(CertificateStatus.VALID);
        certificateRepository.save(certificate);

        applicationService.approve(application.getId(), admin);
        assertEquals(1, advisorRows(applicant));
    }

    // thread pool: three certificates are checked in parallel on the doc-worker pool and all get a result
    @Test
    void threeCertificatesAreCheckedOnThePool() throws Exception {
        User applicant = newClient();
        AdvisorApplication application = newPendingApplication(applicant);

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String path = fileStorageService.store(newPdf(), "advisor_applications");
            ids.add(newCertificate(application, CertificateStatus.PROCESSING, path).getId());
        }

        documentProcessor.processAll(application.getId(), applicant.getId(), ids).get(30, TimeUnit.SECONDS);

        List<ApplicationCertificate> checked = certificateRepository.findAllById(ids);
        assertEquals(3, checked.size());
        for (ApplicationCertificate certificate : checked) {
            assertEquals(CertificateStatus.VALID, certificate.getStatus(), "certificate " + certificate.getId());
            assertEquals("PDF", certificate.getDetectedType());
        }
    }
}
