package com.example.userservice.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Placeholder until the booking service is connected: logs what would be cancelled and cancels nothing. */
@Slf4j
@Component
public class LoggingAppointmentCancellation implements AppointmentCancellation {

    @Override
    public int cancelUpcomingOfClient(Long clientUserId, Long adminUserId, String reason) {
        log.info("[not connected] would cancel the upcoming appointments of client {} (deactivated by admin {}): {}",
                clientUserId, adminUserId, reason);
        return 0;
    }

    @Override
    public int cancelUpcomingOfAdvisor(Long advisorId, Long advisorUserId, String reason) {
        log.info("[not connected] would cancel the upcoming appointments of advisor {} (user {}): {}",
                advisorId, advisorUserId, reason);
        return 0;
    }
}
