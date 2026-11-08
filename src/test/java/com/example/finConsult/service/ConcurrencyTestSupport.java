package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.exception.NotEligible;
import com.example.finConsult.exception.SlotUnavailable;
import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.*;
import com.example.finConsult.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared setup for every concurrency test. All of them run against the separate finconsult_test
 * database and share ONE Spring context (same annotations = cached context, so it only starts once).
 *
 * Do NOT put @Transactional on a test class: every thread needs its own committed transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
abstract class ConcurrencyTestSupport {

    @Autowired protected UserRepository userRepository;
    @Autowired protected AdvisorRepository advisorRepository;
    @Autowired protected ServiceTypeRepository serviceTypeRepository;
    @Autowired protected AvailabilitySlotRepository availabilitySlotRepository;
    @Autowired protected AppointmentRepository appointmentRepository;
    @Autowired protected AdvisorApplicationRepository applicationRepository;
    @Autowired protected ApplicationCertificateRepository certificateRepository;
    @Autowired protected KycDocumentRepository kycDocumentRepository;
    @Autowired protected FileStorageService fileStorageService;
    @Autowired protected PlatformTransactionManager transactionManager;

    // running tasks at the same moment

    protected record Result(int successes, int refused, int other) {}

    protected Result runAtOnce(int copies, Runnable task) throws Exception {
        return runAtOnce(java.util.Collections.nCopies(copies, task));
    }

    // every task gets its own thread, all are released together, and each one is counted by how it ended
    protected Result runAtOnce(List<Runnable> tasks) throws Exception {
        int threads = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);   // every thread is in place
        CountDownLatch go = new CountDownLatch(1);            // released together
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (Runnable task : tasks) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    task.run();
                    successes.incrementAndGet();
                } catch (Throwable e) {
                    if (isRefusal(e)) {
                        refused.incrementAndGet();
                    } else {
                        e.printStackTrace();
                        other.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        go.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "tasks did not finish in time");
        pool.shutdown();
        return new Result(successes.get(), refused.get(), other.get());
    }

    // a clean no: a business rule, or the database or a version check saying no (possibly wrapped by Spring)
    protected boolean isRefusal(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof InvalidRequest || t instanceof NotEligible || t instanceof SlotUnavailable
                    || t instanceof InformationNotFound
                    || t instanceof DataIntegrityViolationException
                    || t instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }

    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    // test data

    protected User newClient() {
        User user = new User();
        user.setEmail("race-" + UUID.randomUUID() + "@test.com");
        user.setPassword("not-used");
        user.setRole(UserRole.CLIENT);
        user.setUserStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    protected User admin() {
        List<User> admins = userRepository.findByRole(UserRole.ADMIN);
        return admins.isEmpty() ? newUser(UserRole.ADMIN) : admins.get(0);
    }

    protected Advisor seededAdvisor() {
        return advisorRepository.findAll().stream()
                .filter(a -> a.isActive() && a.getSpecialty() == Specialty.GENERAL)
                .findFirst()
                .orElseGet(() -> {
                    Advisor advisor = new Advisor();
                    advisor.setUser(newUser(UserRole.ADVISOR));
                    advisor.setTitle("Test advisor");
                    advisor.setSpecialty(Specialty.GENERAL);
                    advisor.setActive(true);
                    return advisorRepository.save(advisor);
                });
    }

    protected ServiceType freeService() {
        return serviceTypeRepository.findAll().stream()
                .filter(s -> s.isActive() && !s.isRequiresKyc())
                .findFirst()
                .orElseGet(() -> {
                    ServiceType service = new ServiceType();
                    service.setName("Race test service " + UUID.randomUUID());
                    service.setDurationMinutes(30);
                    service.setRequiresKyc(false);
                    service.setActive(true);
                    return serviceTypeRepository.save(service);
                });
    }

    private User newUser(UserRole role) {
        User user = new User();
        user.setEmail("race-" + UUID.randomUUID() + "@test.com");
        user.setPassword("not-used");
        user.setRole(role);
        user.setUserStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    // an AVAILABLE slot far in the future (random day, so repeated runs never clash)
    protected AvailabilitySlot newFutureSlot(Advisor advisor) {
        LocalDateTime start = LocalDateTime.now().plusYears(1).plusDays(ThreadLocalRandom.current().nextInt(1, 4000));
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setAdvisor(advisor);
        slot.setStartTime(start);
        slot.setEndTime(start.plusHours(1));
        slot.setStatus(SlotStatus.AVAILABLE);
        return availabilitySlotRepository.save(slot);
    }

    protected void giveApprovedIdentity(User user) {
        kycDocumentRepository.save(newKycDocument(user, DocumentType.NATIONAL_ID, KycStatus.APPROVED, "/uploads/none.pdf"));
    }

    protected KycDocument newKycDocument(User user, DocumentType type, KycStatus status, String path) {
        KycDocument doc = new KycDocument();
        doc.setUser(user);
        doc.setDocumentType(type);
        doc.setFilePath(path);
        doc.setOriginalFileName("id.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(10);
        doc.setStatus(status);
        return doc;
    }

    protected AdvisorApplication newPendingApplication(User applicant) {
        AdvisorApplication application = new AdvisorApplication();
        application.setUser(applicant);
        application.setTitle("Test advisor");
        application.setSpecialty(Specialty.GENERAL);
        application.setYearsOfExperience(3);
        application.setStatus(KycStatus.PENDING);
        return applicationRepository.save(application);
    }

    protected ApplicationCertificate newCertificate(AdvisorApplication application, CertificateStatus status, String path) {
        ApplicationCertificate certificate = new ApplicationCertificate();
        certificate.setApplication(application);
        certificate.setFilePath(path);
        certificate.setOriginalFileName("cert.pdf");
        certificate.setContentType("application/pdf");
        certificate.setFileSize(10);
        certificate.setStatus(status);
        return certificateRepository.save(certificate);
    }

    // every file gets different content, so the duplicate check does not flag it
    protected MockMultipartFile newPdf() {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf",
                ("%PDF-1.4 " + UUID.randomUUID()).getBytes());
    }
}
