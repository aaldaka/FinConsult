package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.model.enums.UserRole;
import com.example.finConsult.model.enums.UserStatus;
import com.example.finConsult.model.response.UserSummaryResponse;
import com.example.finConsult.repository.AppointmentRepository;
import com.example.finConsult.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Admin-driven account deactivation for clients (advisors go through
 * {@link AdvisorService#deactivateAdvisor}, which has its own cascade logic).
 */
@Slf4j
@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final AuditLogService auditLogService;
    private final ResponseMapper mapper;

    /** @return clients that are not deactivated, for the admin deactivate picker */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getActiveClients() {
        return userRepository.findByRoleAndUserStatusNotOrderByIdAsc(UserRole.CLIENT, UserStatus.INACTIVE).stream()
                .map(u -> new UserSummaryResponse(u.getId(), mapper.nameOf(u), u.getEmail()))
                .toList();
    }

    /** Cascades to the client's pending/confirmed appointments via the admin cancel path, so no strike is added. */
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

        int cancelledCount = 0;
        if (user.getRole() == UserRole.CLIENT) {
            List<Appointment> upcoming = appointmentRepository
                    .findByClientAndStatusIn(user, List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
            for (Appointment appt : upcoming) {
                appointmentService.adminCancelAppointment(appt.getId(), "Client account was deactivated", admin);
            }
            cancelledCount = upcoming.size();
        }

        log.info("User {} deactivated by admin {} ({} appointments cancelled)", userId, admin.getId(), cancelledCount);
    }
}
