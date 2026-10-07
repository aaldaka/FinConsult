package com.example.finConsult.service;

import com.example.finConsult.exception.EmailExists;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.*;
import com.example.finConsult.model.request.AdvisorRequest;
import com.example.finConsult.model.request.UpdateAdvisorRequest;
import com.example.finConsult.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Manages advisor accounts: admin-driven creation, profile self-updates, lookups by
 * specialty, and deactivation (which also cancels the advisor's upcoming appointments).
 */
@Service
@AllArgsConstructor
public class AdvisorService {

    private final AdvisorRepository advisorRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentService appointmentService;
    private final AuditLogService auditLogService;

    /** Admin creates an advisor account, skipping email verification since it's admin-trusted. */
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
        auditLogService.log(admin, AuditAction.ADVISOR_CREATED, "Advisor", saved.getId(),
                "Created advisor account for " + req.getEmail());
        return saved;
    }

    public Advisor getById(Long advisorId) {
        return advisorRepository.findById(advisorId)
                .orElseThrow(() -> new InformationNotFound("Advisor not found"));
    }

    public List<Advisor> findBySpecialty(Specialty specialty) {
        return advisorRepository.findBySpecialtyAndActiveTrue(specialty);
    }

    public List<Advisor> findAllActive() {
        return advisorRepository.findByActiveTrue();
    }

    /** Cascades to the advisor's pending/confirmed appointments, cancelling each so no client is left stranded. */
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

        auditLogService.log(admin, AuditAction.ADVISOR_DEACTIVATED, "Advisor", advisorId, "Reason: " + reason);


        List<Appointment> upcoming = appointmentRepository
                .findByAdvisorAndStatusIn(advisor, List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));

        for (Appointment appt : upcoming) {
            appointmentService.cancelAppointment(appt.getId(), advisorUser, "Advisor is no longer available: " + reason);
        }

    }

    /** Partial update — only non-null fields on the request are applied. */
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