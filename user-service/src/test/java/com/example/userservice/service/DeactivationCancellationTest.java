package com.example.userservice.service;

import com.example.userservice.integration.AppointmentCancellation;
import com.example.userservice.integration.AuditRecorder;
import com.example.userservice.integration.RestAppointmentCancellation;
import com.example.userservice.mapper.ResponseMapper;
import com.example.userservice.model.Advisor;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import com.example.userservice.repository.AdvisorRepository;
import com.example.userservice.repository.ProfileRepository;
import com.example.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// no Spring and no database. A transaction is simulated with the synchronization manager, which is all AfterCommit
// looks at, so the tests can check that the call to booking waits for the commit and is skipped on a rollback
@ExtendWith(MockitoExtension.class)
class DeactivationCancellationTest {

    @Mock UserRepository userRepository;
    @Mock AdvisorRepository advisorRepository;
    @Mock ProfileRepository profileRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AppointmentCancellation appointmentCancellation;
    @Mock AuditRecorder auditRecorder;
    @Mock ResponseMapper mapper;

    @AfterEach
    void endTheSimulatedTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private User user(long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setUserStatus(UserStatus.ACTIVE);
        return user;
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private void rollBack() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    @Test
    void theClientCancellationWaitsForTheCommit() {
        User client = user(10, UserRole.CLIENT);
        when(userRepository.findById(10L)).thenReturn(Optional.of(client));
        UserService service = new UserService(userRepository, appointmentCancellation, auditRecorder, mapper);
        TransactionSynchronizationManager.initSynchronization();

        service.deactivateUser(10L, user(99, UserRole.ADMIN));

        verify(appointmentCancellation, never()).cancelUpcomingOfClient(anyLong(), anyLong(), anyString());
        commit();
        verify(appointmentCancellation).cancelUpcomingOfClient(10L, 99L, "Client account was deactivated");
    }

    @Test
    void aRolledBackDeactivationAsksBookingForNothing() {
        User client = user(10, UserRole.CLIENT);
        when(userRepository.findById(10L)).thenReturn(Optional.of(client));
        UserService service = new UserService(userRepository, appointmentCancellation, auditRecorder, mapper);
        TransactionSynchronizationManager.initSynchronization();

        service.deactivateUser(10L, user(99, UserRole.ADMIN));
        rollBack();

        verify(appointmentCancellation, never()).cancelUpcomingOfClient(any(), any(), any());
    }

    @Test
    void theAdvisorCancellationWaitsForTheCommit() {
        User advisorUser = user(105, UserRole.ADVISOR);
        Advisor advisor = new Advisor();
        advisor.setId(5L);
        advisor.setUser(advisorUser);
        advisor.setActive(true);
        when(advisorRepository.findById(5L)).thenReturn(Optional.of(advisor));
        AdvisorService service = new AdvisorService(advisorRepository, userRepository, profileRepository, passwordEncoder,
                appointmentCancellation, auditRecorder);
        TransactionSynchronizationManager.initSynchronization();

        service.deactivateAdvisor(5L, "left", user(99, UserRole.ADMIN));

        verify(appointmentCancellation, never()).cancelUpcomingOfAdvisor(any(), any(), any());
        commit();
        verify(appointmentCancellation).cancelUpcomingOfAdvisor(5L, 105L, "Advisor is no longer available: left");
        assertFalse(advisor.isActive());
    }

    @Test
    void aBookingThatIsDownDoesNotThrowAndDoesNotUndoTheDeactivation() {
        User client = user(10, UserRole.CLIENT);
        when(userRepository.findById(10L)).thenReturn(Optional.of(client));
        AppointmentCancellation unreachableBooking = new RestAppointmentCancellation(
                "http://127.0.0.1:1", "some-key"); // port 1: nothing listens, the connection is refused at once
        UserService service = new UserService(userRepository, unreachableBooking, auditRecorder, mapper);
        TransactionSynchronizationManager.initSynchronization();

        assertDoesNotThrow(() -> service.deactivateUser(10L, user(99, UserRole.ADMIN)));
        assertDoesNotThrow(this::commit);

        assertEquals(UserStatus.INACTIVE, client.getUserStatus());
        verify(userRepository).save(client);
    }
}
