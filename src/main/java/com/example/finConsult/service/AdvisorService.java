package com.example.finConsult.service;

import com.example.finConsult.exception.EmailExists;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.*;
import com.example.finConsult.model.request.AdvisorRequest;
import com.example.finConsult.model.request.UpdateAdvisorRequest;
import com.example.finConsult.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class AdvisorService {

    private final AdvisorRepository advisorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Advisor createAdvisor(AdvisorRequest req) {
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
        // wire functionality of requesting to become an advisor throuh application
        return advisorRepository.save(advisor);
    }

    public Advisor getById(Long advisorId) {
        return advisorRepository.findById(advisorId)
                .orElseThrow(() -> new InformationNotFound("Advisor not found"));
    }

    public List<Advisor> findBySpecialty(Specialty specialty) {
        return advisorRepository.findBySpecialtyAndActiveTrue(specialty);
    }

    @Transactional
    public void deactivateAdvisor(Long advisorId, String reason) {
        Advisor advisor = getById(advisorId);
        advisor.setActive(false);
        advisorRepository.save(advisor);

        User advisorUser = advisor.getUser();
        advisorUser.setUserStatus(UserStatus.INACTIVE);
        userRepository.save(advisorUser);

        List<Appointment> upcoming = appointmentRepository
                .findByAdvisorAndStatusIn(advisor, List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));

        for (Appointment appt : upcoming) {
            appt.setStatus(AppointmentStatus.CANCELLED);
            appt.setCancellationReason("Advisor is no longer available: " + reason);
            appt.setCancelledAt(LocalDateTime.now());
            appointmentRepository.save(appt);

            appt.getSlot().setStatus(SlotStatus.AVAILABLE);
            availabilitySlotRepository.save(appt.getSlot());

            Notification notif = new Notification();
            notif.setUser(appt.getClient());
            notif.setType(NotificationType.APPOINTMENT_CANCELLED);
            notif.setMessage("Your appointment with " + advisor.getTitle() +
                    " was cancelled because the advisor is no longer available. Please book with another advisor");
            notif.setRelatedAppointment(appt);
            notificationRepository.save(notif);
        }
    }

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