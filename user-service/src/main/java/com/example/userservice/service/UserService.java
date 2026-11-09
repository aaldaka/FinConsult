package com.example.userservice.service;

import com.example.userservice.exception.InformationNotFound;
import com.example.userservice.exception.InvalidRequest;
import com.example.userservice.integration.AppointmentCancellation;
import com.example.userservice.integration.AuditRecorder;
import com.example.userservice.mapper.ResponseMapper;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.AuditAction;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import com.example.userservice.model.response.UserSummaryResponse;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-driven account deactivation for clients (advisors go through
 * {@link AdvisorService#deactivateAdvisor}, which has its own cascade logic).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AppointmentCancellation appointmentCancellation;
    private final AuditRecorder auditRecorder;
    private final ResponseMapper mapper;

    /** @return clients that are not deactivated, for the admin deactivate picker */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getActiveClients() {
        return userRepository.findByRoleAndUserStatusNotOrderByIdAsc(UserRole.CLIENT, UserStatus.INACTIVE).stream()
                .map(u -> new UserSummaryResponse(u.getId(), mapper.nameOf(u), u.getEmail()))
                .toList();
    }

    /** Also asks the booking side to cancel the client's pending/confirmed appointments (no strike is added). */
    @Transactional
    public void deactivateUser(Long userId, User admin) { // client deactivation
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InformationNotFound("User not found"));

        if (user.getRole() == UserRole.ADVISOR) { // separate advisor api
            throw new InvalidRequest("Deactivate advisors through the advisor deactivation endpoint instead");
        }

        user.setUserStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        auditRecorder.record(admin.getId(), AuditAction.USER_DEACTIVATED, "User", userId, "Account deactivated");

        int cancelledCount = 0;
        if (user.getRole() == UserRole.CLIENT) {
            cancelledCount = appointmentCancellation.cancelUpcomingOfClient(userId, admin.getId(), "Client account was deactivated");
        }

        log.info("User {} deactivated by admin {} ({} appointments cancelled)", userId, admin.getId(), cancelledCount);
    }
}
