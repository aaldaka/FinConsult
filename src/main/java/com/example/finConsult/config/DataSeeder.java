package com.example.finConsult.config;

import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.*;
import com.example.finConsult.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Populates an empty development database with demo data: service types, an admin,
 * one advisor per specialty (two for GENERAL), clients, availability slots,
 * approved KYC documents for one client, a few sample appointments, and advisor
 * applications plus a flagged KYC document for the Project 3 review flows.
 * <p>
 * Every step checks for existing data first, so restarting the app never creates duplicates.
 * Runs only under the {@code dev} profile. All seeded accounts use the same dev-only password.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("dev") // fully qualified because the Profile entity has the same name
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String DEV_PASSWORD = "Password123!"; // demo accounts only, documented in the README

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final AdvisorRepository advisorRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final AdvisorApplicationRepository applicationRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        ServiceType general = seedService("General Consultation", "Plain financial advice", 30, null, Specialty.GENERAL);
        seedService("Retirement Planning", "Retirement savings and planning", 30, null, Specialty.RETIREMENT_PLANNING);
        ServiceType insurance = seedService("Insurance Review", "Insurance coverage advice", 30, null, Specialty.INSURANCE);
        ServiceType investment = seedService("Investment Planning", "Stocks and portfolio consultation", 45, DocumentType.NATIONAL_ID, Specialty.INVESTMENTS);
        seedService("Loan Consultation", "Personal loan advice", 30, DocumentType.INCOME_PROOF, Specialty.LOANS);
        seedService("Mortgage Consultation", "Home financing advice", 60, DocumentType.PROOF_OF_ADDRESS, Specialty.MORTGAGES);
        seedService("Business Banking Consultation", "Accounts and financing for small businesses", 45, DocumentType.PASSPORT, Specialty.BUSINESS_BANKING);

        User admin = seedUser("abdulaziz.alkhalifa@finconsult.com", UserRole.ADMIN, "Abdulaziz", "Al-Khalifa", "+97333000001");

        // one advisor per specialty, plus a second GENERAL advisor
        Advisor reem = seedAdvisor("reem.almannai@finconsult.com", "Reem", "Al-Mannai", "+97333000002",
                "Financial Planner", Specialty.GENERAL, 7);
        Advisor noor = seedAdvisor("noor.alzayani@finconsult.com", "Noor", "Al-Zayani", "+97333000003",
                "Senior Financial Advisor", Specialty.GENERAL, 11);
        seedAdvisor("abdulla.alaali@finconsult.com", "Abdulla", "Al-Aali", "+97333000004",
                "Loan Officer", Specialty.LOANS, 6);
        seedAdvisor("alya.alsayed@finconsult.com", "Alya", "Al-Sayed", "+97333000005",
                "Mortgage Specialist", Specialty.MORTGAGES, 8);
        Advisor nasser = seedAdvisor("nasser.alkooheji@finconsult.com", "Nasser", "Al-Kooheji", "+97333000006",
                "Senior Investment Advisor", Specialty.INVESTMENTS, 12);
        seedAdvisor("ghalya.alfadhel@finconsult.com", "Ghalya", "Al-Fadhel", "+97333000007",
                "Retirement Planning Specialist", Specialty.RETIREMENT_PLANNING, 9);
        Advisor samar = seedAdvisor("samar.almahmood@finconsult.com", "Samar", "Al-Mahmood", "+97333000008",
                "Insurance Advisor", Specialty.INSURANCE, 5);
        seedAdvisor("fatima.aljalahma@finconsult.com", "Fatima", "Al-Jalahma", "+97333000009",
                "Business Banking Manager", Specialty.BUSINESS_BANKING, 10);

        User maryam = seedUser("maryam.alhammadi@example.com", UserRole.CLIENT, "Maryam", "Al-Hammadi", "+97333000011");
        User hamad = seedUser("hamad.alansari@example.com", UserRole.CLIENT, "Hamad", "Al-Ansari", "+97333000012");
        User hessa = seedUser("hessa.albinali@example.com", UserRole.CLIENT, "Hessa", "Al-Binali", "+97333000013");
        User salman = seedUser("salman.alderazi@example.com", UserRole.CLIENT, "Salman", "Al-Derazi", "+97333000014");

        // Maryam is the demo client with approved KYC so she can book the KYC-gated services
        seedKycDocuments(maryam, admin);

        // demo data for the Project 3 flows: applications to review and a flagged KYC document
        seedAdvisorApplications(hessa, salman, hamad, admin);
        seedFlaggedKyc(salman);

        for (Advisor advisor : advisorRepository.findAll()) {
            seedSlots(advisor);
        }

        seedAppointments(maryam, hamad, hessa, reem, nasser, samar, general, investment, insurance);

        log.info("Data seeding complete: {} service types, {} advisors, {} users, {} slots, {} appointments, {} advisor applications",
                serviceTypeRepository.count(), advisorRepository.count(), userRepository.count(),
                slotRepository.count(), appointmentRepository.count(), applicationRepository.count());
    }

    private ServiceType seedService(String name, String description, int minutes,
                                    DocumentType requiredDocument, Specialty requiredSpecialty) {
        return serviceTypeRepository.findByName(name).orElseGet(() -> {
            ServiceType service = new ServiceType();
            service.setName(name);
            service.setDescription(description);
            service.setDurationMinutes(minutes);
            service.setRequiresKyc(requiredDocument != null); // only services that move money need KYC
            service.setRequiredDocumentType(requiredDocument);
            service.setRequiredSpecialty(requiredSpecialty);
            service.setActive(true);
            return serviceTypeRepository.save(service);
        });
    }

    private User seedUser(String email, UserRole role, String first, String last, String phone) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(DEV_PASSWORD));
            user.setRole(role);
            user.setUserStatus(UserStatus.ACTIVE); // skips the email verification step for demo accounts
            user = userRepository.save(user);

            Profile profile = new Profile();
            profile.setUser(user);
            profile.setFirstName(first);
            profile.setLastName(last);
            profile.setPhone(phone);
            profileRepository.save(profile); // no picture, the default avatar is used
            return user;
        });
    }

    private Advisor seedAdvisor(String email, String first, String last, String phone,
                                String title, Specialty specialty, int years) {
        User user = seedUser(email, UserRole.ADVISOR, first, last, phone);
        return advisorRepository.findByUser(user).orElseGet(() -> {
            Advisor advisor = new Advisor();
            advisor.setUser(user);
            advisor.setTitle(title);
            advisor.setSpecialty(specialty);
            advisor.setYearsOfExperience(years);
            advisor.setActive(true);
            return advisorRepository.save(advisor);
        });
    }

    /** Gives the client one approved document of every type (placeholder files, nothing is stored on disk). */
    private void seedKycDocuments(User client, User reviewer) {
        if (!kycDocumentRepository.findByUser(client).isEmpty()) {
            return;
        }
        for (DocumentType type : DocumentType.values()) {
            KycDocument doc = new KycDocument();
            doc.setUser(client);
            doc.setDocumentType(type);
            doc.setFilePath("seed/" + type.name().toLowerCase() + ".pdf");
            doc.setOriginalFileName(type.name().toLowerCase() + ".pdf");
            doc.setContentType("application/pdf");
            doc.setFileSize(0);
            doc.setStatus(KycStatus.APPROVED);
            doc.setReviewedBy(reviewer);
            doc.setReviewedAt(LocalDateTime.now());
            kycDocumentRepository.save(doc);
        }
    }

    /** Demo advisor applications: one ready to approve, one with a flagged certificate, one already rejected. */
    private void seedAdvisorApplications(User hessa, User salman, User hamad, User admin) {
        // ready to approve live: both certificates passed the background checks
        if (applicationRepository.findByUser(hessa).isEmpty()) {
            AdvisorApplication ready = seedApplication(hessa, "Retirement Planner", Specialty.RETIREMENT_PLANNING, 4,
                    KycStatus.PENDING, null, null);
            seedCertificate(ready, "hessa-cfa-level-1.pdf", CertificateStatus.VALID);
            seedCertificate(ready, "hessa-banking-diploma.pdf", CertificateStatus.VALID);
        }

        // needs a closer look: one certificate was flagged by the background check
        if (applicationRepository.findByUser(salman).isEmpty()) {
            AdvisorApplication flagged = seedApplication(salman, "Insurance Advisor", Specialty.INSURANCE, 2,
                    KycStatus.PENDING, null, null);
            seedCertificate(flagged, "salman-insurance-license.pdf", CertificateStatus.VALID);
            seedCertificate(flagged, "salman-certificate-copy.pdf", CertificateStatus.FLAGGED);
        }

        // history: an earlier application that was rejected, counts as 1 of the 3 attempts
        if (applicationRepository.findByUser(hamad).isEmpty()) {
            seedApplication(hamad, "Loan Officer", Specialty.LOANS, 1,
                    KycStatus.REJECTED, admin, "Needs at least 2 years of experience");
        }
    }

    private AdvisorApplication seedApplication(User applicant, String title, Specialty specialty, int years,
                                               KycStatus status, User reviewer, String rejectionReason) {
        AdvisorApplication application = new AdvisorApplication();
        application.setUser(applicant);
        application.setTitle(title);
        application.setSpecialty(specialty);
        application.setYearsOfExperience(years);
        application.setStatus(status);
        if (reviewer != null) {
            application.setReviewedBy(reviewer);
            application.setReviewedAt(LocalDateTime.now().minusDays(5));
            application.setRejectionReason(rejectionReason);
        }
        return applicationRepository.save(application);
    }

    private void seedCertificate(AdvisorApplication application, String fileName, CertificateStatus status) {
        ApplicationCertificate certificate = new ApplicationCertificate();
        certificate.setApplication(application);
        certificate.setFilePath(writeSeedFile("advisor_applications", fileName));
        certificate.setOriginalFileName(fileName);
        certificate.setContentType("application/pdf");
        certificate.setFileSize(0);
        certificate.setStatus(status);
        certificate.setDetectedType("PDF");
        certificateRepository.save(certificate);
    }

    /** A pending KYC document already flagged by the check, so the admin review shows the flag reason. */
    private void seedFlaggedKyc(User client) {
        if (!kycDocumentRepository.findByUser(client).isEmpty()) {
            return;
        }
        KycDocument doc = new KycDocument();
        doc.setUser(client);
        doc.setDocumentType(DocumentType.PASSPORT);
        doc.setFilePath(writeSeedFile("kyc_documents", "salman-passport.pdf"));
        doc.setOriginalFileName("salman-passport.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(0);
        doc.setStatus(KycStatus.PENDING);
        doc.setDetectedType("PDF");
        doc.setFlagReason("Same file was uploaded by another account");
        kycDocumentRepository.save(doc);
    }

    // writes a small placeholder file under uploads/ so the admin download link works
    private String writeSeedFile(String folder, String fileName) {
        Path file = Path.of("uploads", folder, "seed-" + fileName);
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.writeString(file, "%PDF-1.4 seed file " + fileName);
            }
        } catch (IOException e) {
            log.warn("Could not write seed file {}", file);
        }
        return "/uploads/" + folder + "/seed-" + fileName;
    }

    /** Creates hourly slots for the next five working days (Sunday to Thursday, the Bahrain work week). */
    private void seedSlots(Advisor advisor) {
        if (!slotRepository.findByAdvisor(advisor).isEmpty()) {
            return;
        }
        LocalDate day = LocalDate.now();
        int daysCreated = 0;
        while (daysCreated < 5) {
            day = day.plusDays(1);
            if (day.getDayOfWeek() == DayOfWeek.FRIDAY || day.getDayOfWeek() == DayOfWeek.SATURDAY) {
                continue;
            }
            for (int hour : new int[]{9, 10, 11, 14}) {
                saveSlot(advisor, day.atTime(hour, 0), SlotStatus.AVAILABLE);
            }
            daysCreated++;
        }
    }

    private AvailabilitySlot saveSlot(Advisor advisor, LocalDateTime start, SlotStatus status) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setAdvisor(advisor);
        slot.setStartTime(start);
        slot.setEndTime(start.plusHours(1));
        slot.setStatus(status);
        return slotRepository.save(slot);
    }

    private void seedAppointments(User maryam, User hamad, User hessa, Advisor reem, Advisor nasser,
                                  Advisor samar, ServiceType general, ServiceType investment, ServiceType insurance) {
        if (appointmentRepository.count() > 0) {
            return;
        }
        seedAppointment(maryam, reem, general, nextFreeSlot(reem), AppointmentStatus.CONFIRMED, "Wants to review monthly budget");
        seedAppointment(maryam, nasser, investment, nextFreeSlot(nasser), AppointmentStatus.PENDING, "Interested in building a stock portfolio");
        seedAppointment(hamad, samar, insurance, nextFreeSlot(samar), AppointmentStatus.PENDING, "Family health coverage");

        // a finished appointment needs a slot in the past
        AvailabilitySlot pastSlot = saveSlot(reem, LocalDateTime.now().minusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0), SlotStatus.AVAILABLE);
        seedAppointment(hessa, reem, general, pastSlot, AppointmentStatus.COMPLETED, "First consultation");
    }

    private AvailabilitySlot nextFreeSlot(Advisor advisor) {
        return slotRepository.findByAdvisorAndStatus(advisor, SlotStatus.AVAILABLE).stream()
                .min(Comparator.comparing(AvailabilitySlot::getStartTime))
                .orElseThrow();
    }

    private void seedAppointment(User client, Advisor advisor, ServiceType service,
                                 AvailabilitySlot slot, AppointmentStatus status, String notes) {
        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setAdvisor(advisor);
        appointment.setServiceType(service);
        appointment.setSlot(slot);
        appointment.setStatus(status);
        appointment.setNotes(notes);
        appointmentRepository.save(appointment);

        slot.setStatus(SlotStatus.BOOKED); // a booked slot must not be offered to anyone else
        slotRepository.save(slot);
    }
}