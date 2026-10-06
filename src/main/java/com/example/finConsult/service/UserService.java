package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.model.enums.UserRole;
import com.example.finConsult.model.enums.UserStatus;
import com.example.finConsult.repository.AppointmentRepository;
import com.example.finConsult.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final AuditLogService auditLogService;

    @Transactional
    public void deactivateUser(Long userId, User admin) { // client deactivation
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InformationNotFound("User not found"));

        if (user.getRole() == UserRole.ADVISOR) { // separate advisor api
            throw new InvalidRequest("Deactivate advisors through the advisor deactivation endpoint instead");
        }

        user.setUserStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        auditLogService.log(admin, AuditAction.USER_DEACTIVATED, "User", userId, "Account deactivated");

        if (user.getRole() == UserRole.CLIENT) {
            List<Appointment> upcoming = appointmentRepository
                    .findByClientAndStatusIn(user, List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
            for (Appointment appt : upcoming) {
                appointmentService.adminCancelAppointment(appt.getId(), "Client account was deactivated", admin);
            }
        }
    }
}
