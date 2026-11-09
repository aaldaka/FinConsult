package com.example.userservice.service;

import com.example.userservice.exception.EmailExists;
import com.example.userservice.exception.InformationNotFound;
import com.example.userservice.exception.InvalidRequest;
import com.example.userservice.integration.AppointmentCancellation;
import com.example.userservice.integration.AuditRecorder;
import com.example.userservice.model.Advisor;
import com.example.userservice.model.Profile;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.AuditAction;
import com.example.userservice.model.enums.Specialty;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import com.example.userservice.model.request.AdvisorRequest;
import com.example.userservice.model.request.UpdateAdvisorRequest;
import com.example.userservice.repository.AdvisorRepository;
import com.example.userservice.repository.ProfileRepository;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages advisor accounts: admin-driven creation, profile self-updates, lookups by
 * specialty, and deactivation (which also asks the booking side to cancel the advisor's upcoming appointments).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorService {

    private final AdvisorRepository advisorRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentCancellation appointmentCancellation;
    private final AuditRecorder auditRecorder;

    /** Admin creates an advisor account, skipping email verification since it's admin-trusted. No email is sent. */
    @Transactional
    public Advisor createAdvisor(AdvisorRequest req, User admin) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new EmailExists("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(UserRole.ADVISOR);
        user.setUserStatus(UserStatus.ACTIVE); // admin-created, so skip email verification
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setFirstName(req.getFirstName());
        profile.setLastName(req.getLastName());
        profileRepository.save(profile);

        Advisor advisor = new Advisor();
        advisor.setUser(user);
        advisor.setTitle(req.getTitle());
        advisor.setSpecialty(req.getSpecialty() != null ? req.getSpecialty() : Specialty.GENERAL);
        advisor.setYearsOfExperience(req.getYearsOfExperience());
        advisor.setActive(true);
        profile.setProfilePicturePath("/default.jpg");
        // wire functionality of requesting to become an advisor throuh application
        Advisor saved = advisorRepository.save(advisor);

        auditRecorder.record(admin.getId(), AuditAction.ADVISOR_CREATED, "Advisor", saved.getId(),
                "Created advisor account for " + req.getEmail());
        log.info("Advisor {} created by admin {} (user {})", saved.getId(), admin.getId(), user.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Advisor getByUser(User user) {
        return advisorRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Advisor profile not found"));
    }

    @Transactional(readOnly = true)
    public Advisor getById(Long advisorId) {
        return advisorRepository.findById(advisorId)
                .orElseThrow(() -> new InformationNotFound("Advisor not found"));
    }

    @Transactional(readOnly = true)
    public List<Advisor> findBySpecialty(Specialty specialty) {
        return advisorRepository.findBySpecialtyAndActiveTrue(specialty);
    }

    @Transactional(readOnly = true)
    public List<Advisor> findAllActive() {
        return advisorRepository.findByActiveTrue();
    }

    /** Also asks the booking side to cancel the advisor's pending/confirmed appointments, so no client is left stranded. */
    @Transactional
    public void deactivateAdvisor(Long advisorId, String reason, User admin) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequest("A reason is required");
        }

        Advisor advisor = getById(advisorId);
        advisor.setActive(false);
        advisorRepository.save(advisor);

        User advisorUser = advisor.getUser();
        advisorUser.setUserStatus(UserStatus.INACTIVE);
        userRepository.save(advisorUser);

        auditRecorder.record(admin.getId(), AuditAction.ADVISOR_DEACTIVATED, "Advisor", advisorId, "Reason: " + reason);

        int cancelled = appointmentCancellation.cancelUpcomingOfAdvisor(advisorId, advisorUser.getId(),
                "Advisor is no longer available: " + reason);

        log.info("Advisor {} deactivated by admin {} ({} appointments cancelled)", advisorId, admin.getId(), cancelled);
    }

    /** Partial update, only non-null fields are applied. */
    @Transactional
    public Advisor updateAdvisor(User user, UpdateAdvisorRequest updateAdvisorRequest){
        Advisor advisor = advisorRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Advisor profile was not found"));

        if(updateAdvisorRequest.getSpecialty()!=null){
            advisor.setSpecialty(updateAdvisorRequest.getSpecialty());
        }
        if(updateAdvisorRequest.getTitle()!=null){
            advisor.setTitle(updateAdvisorRequest.getTitle());
        }
        if(updateAdvisorRequest.getYearsOfExperience()!=null){
            advisor.setYearsOfExperience(updateAdvisorRequest.getYearsOfExperience());
        }

        advisorRepository.save(advisor);
        return advisor;
    }
}
