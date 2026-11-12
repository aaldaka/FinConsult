package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.InvalidRequest;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.model.request.CreateSlotRequest;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// no Spring and no database: the repository, the user service client, JdbcTemplate and the transaction manager are mocks
@ExtendWith(MockitoExtension.class)
class AvailabilitySlotServiceTest {

    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(?)";

    @Mock AvailabilitySlotRepository availabilitySlotRepository;
    @Mock AdvisorClient advisorClient;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock PlatformTransactionManager transactionManager;

    private AvailabilitySlotService availabilitySlotService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        availabilitySlotService = new AvailabilitySlotService(availabilitySlotRepository, advisorClient, jdbcTemplate, transactionManager);
    }

    private AdvisorInfo advisor(long id, boolean active) {
        return new AdvisorInfo(id, 100 + id, active, "Test Advisor", "Planner", Specialty.GENERAL);
    }

    private AvailabilitySlot newSlot(long advisorId, LocalDateTime start) {
        AvailabilitySlot s = new AvailabilitySlot();
        s.setId(50L);
        s.setAdvisorId(advisorId);
        s.setStartTime(start);
        s.setEndTime(start.plusHours(1));
        s.setStatus(SlotStatus.AVAILABLE);
        return s;
    }

    private CreateSlotRequest request(LocalDateTime start, LocalDateTime end) {
        CreateSlotRequest req = new CreateSlotRequest();
        req.setStartingTime(start);
        req.setFinishingTime(end);
        return req;
    }

    // create

    @Test
    void createSlotInPastFails() {
        when(advisorClient.getById(1L)).thenReturn(advisor(1, true));
        CreateSlotRequest req = request(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        InvalidRequest error = assertThrows(InvalidRequest.class, () -> availabilitySlotService.createSlot(1L, req));

        assertEquals("Slot start time cannot be in the past.", error.getMessage());
        verify(availabilitySlotRepository, never()).save(any());
    }

    @Test
    void createSlotEndingBeforeItStartsFails() {
        when(advisorClient.getById(1L)).thenReturn(advisor(1, true));
        LocalDateTime start = LocalDateTime.now().plusDays(2);

        InvalidRequest error = assertThrows(InvalidRequest.class,
                () -> availabilitySlotService.createSlot(1L, request(start, start.minusHours(1))));

        assertEquals("Slot end time must be after the start time", error.getMessage());
        verify(availabilitySlotRepository, never()).save(any());
    }

    @Test
    void createSlotOverlapFails() {
        AvailabilitySlot existing = newSlot(1, LocalDateTime.now().plusDays(1));
        when(advisorClient.getById(1L)).thenReturn(advisor(1, true));
        when(availabilitySlotRepository.findByAdvisorId(1L)).thenReturn(List.of(existing));

        CreateSlotRequest req = request(existing.getStartTime().plusMinutes(30), existing.getEndTime().plusMinutes(30));

        InvalidRequest error = assertThrows(InvalidRequest.class, () -> availabilitySlotService.createSlot(1L, req));

        assertEquals("Proposed slot overlap with existing slots", error.getMessage());
        verify(availabilitySlotRepository, never()).save(any());
    }

    @Test
    void aSlotStartingWhenAnotherEndsIsNotAnOverlapAndIsSaved() {
        AvailabilitySlot existing = newSlot(1, LocalDateTime.now().plusDays(1));
        when(advisorClient.getById(1L)).thenReturn(advisor(1, true));
        when(availabilitySlotRepository.findByAdvisorId(1L)).thenReturn(List.of(existing));
        when(availabilitySlotRepository.save(any(AvailabilitySlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AvailabilitySlot created = availabilitySlotService.createSlot(1L,
                request(existing.getEndTime(), existing.getEndTime().plusHours(1)));

        assertEquals(1L, created.getAdvisorId());
        assertEquals(SlotStatus.AVAILABLE, created.getStatus());
        assertEquals(existing.getEndTime(), created.getStartTime());
    }

    @Test
    void theAdvisorLockIsTakenBeforeTheOverlapCheck() {
        when(advisorClient.getById(7L)).thenReturn(advisor(7, true));
        when(availabilitySlotRepository.findByAdvisorId(7L)).thenReturn(List.of());
        when(availabilitySlotRepository.save(any(AvailabilitySlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime start = LocalDateTime.now().plusDays(3);

        availabilitySlotService.createSlot(7L, request(start, start.plusHours(1)));

        InOrder order = inOrder(jdbcTemplate, availabilitySlotRepository);
        order.verify(jdbcTemplate).queryForList(LOCK_SQL, 7L); // keyed by the advisor id
        order.verify(availabilitySlotRepository).findByAdvisorId(7L);
        order.verify(availabilitySlotRepository).save(any(AvailabilitySlot.class));
    }

    @Test
    void theAdvisorIsCheckedBeforeATransactionIsOpened() {
        when(advisorClient.getById(7L)).thenReturn(advisor(7, true));
        when(availabilitySlotRepository.findByAdvisorId(7L)).thenReturn(List.of());
        when(availabilitySlotRepository.save(any(AvailabilitySlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime start = LocalDateTime.now().plusDays(3);

        availabilitySlotService.createSlot(7L, request(start, start.plusHours(1)));

        InOrder order = inOrder(advisorClient, transactionManager);
        order.verify(advisorClient).getById(7L);
        order.verify(transactionManager).getTransaction(any());
    }

    @Test
    void anInactiveAdvisorCannotCreateSlotsAndNothingIsLockedOrSaved() {
        when(advisorClient.getById(1L)).thenReturn(advisor(1, false));
        LocalDateTime start = LocalDateTime.now().plusDays(2);

        InvalidRequest error = assertThrows(InvalidRequest.class,
                () -> availabilitySlotService.createSlot(1L, request(start, start.plusHours(1))));

        assertEquals("This advisor is no longer available", error.getMessage());
        verifyNoInteractions(jdbcTemplate, availabilitySlotRepository);
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void anUnknownAdvisorOrADownUserServiceStopsBeforeAnyTransaction() {
        when(advisorClient.getById(1L)).thenThrow(new InformationNotFound("Advisor not found"));
        when(advisorClient.getById(2L)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));
        LocalDateTime start = LocalDateTime.now().plusDays(2);

        assertThrows(InformationNotFound.class, () -> availabilitySlotService.createSlot(1L, request(start, start.plusHours(1))));
        assertThrows(ServiceUnavailable.class, () -> availabilitySlotService.createSlot(2L, request(start, start.plusHours(1))));

        verifyNoInteractions(jdbcTemplate, availabilitySlotRepository);
        verify(transactionManager, never()).getTransaction(any());
    }

    // delete

    @Test
    void deleteRemovesAnOwnAvailableSlotUnderTheSlotLock() {
        AvailabilitySlot slot = newSlot(1, LocalDateTime.now().plusDays(1));
        when(availabilitySlotRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(slot));

        availabilitySlotService.deleteSlot(1L, 50L);

        verify(availabilitySlotRepository).delete(slot);
    }

    @Test
    void deleteOfAnUnknownSlotIsNotFound() {
        when(availabilitySlotRepository.findByIdForUpdate(50L)).thenReturn(Optional.empty());

        InformationNotFound error = assertThrows(InformationNotFound.class, () -> availabilitySlotService.deleteSlot(1L, 50L));

        assertEquals("Slot not found", error.getMessage());
    }

    @Test
    void deleteOfAnotherAdvisorsSlotFails() {
        AvailabilitySlot slot = newSlot(2, LocalDateTime.now().plusDays(1));
        when(availabilitySlotRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(slot));

        InvalidRequest error = assertThrows(InvalidRequest.class, () -> availabilitySlotService.deleteSlot(1L, 50L));

        assertEquals("You can only delete your own slots", error.getMessage());
        verify(availabilitySlotRepository, never()).delete(any());
    }

    @Test
    void deleteOfABookedSlotFails() {
        AvailabilitySlot slot = newSlot(1, LocalDateTime.now().plusDays(1));
        slot.setStatus(SlotStatus.BOOKED);
        when(availabilitySlotRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(slot));

        InvalidRequest error = assertThrows(InvalidRequest.class, () -> availabilitySlotService.deleteSlot(1L, 50L));

        assertEquals("Cannot delete a slot that is not available", error.getMessage());
        verify(availabilitySlotRepository, never()).delete(any());
    }

    // lookups

    @Test
    void anInactiveAdvisorHasNoAvailableSlots() {
        when(advisorClient.getById(1L)).thenReturn(advisor(1, false));

        assertTrue(availabilitySlotService.getAvailableSlots(1L).isEmpty());
        verifyNoInteractions(availabilitySlotRepository);
    }

    @Test
    void anUnknownAdvisorIsNotFoundWhenListingSlots() {
        when(advisorClient.getById(9L)).thenThrow(new InformationNotFound("Advisor not found"));

        assertThrows(InformationNotFound.class, () -> availabilitySlotService.getAvailableSlots(9L));
        verifyNoInteractions(availabilitySlotRepository);
    }

    @Test
    void anActiveAdvisorsAvailableSlotsAreListed() {
        List<AvailabilitySlot> slots = List.of(newSlot(1, LocalDateTime.now().plusDays(1)));
        when(advisorClient.getById(1L)).thenReturn(advisor(1, true));
        when(availabilitySlotRepository.findByAdvisorIdAndStatus(1L, SlotStatus.AVAILABLE)).thenReturn(slots);

        assertEquals(slots, availabilitySlotService.getAvailableSlots(1L));
    }

    @Test
    void mySlotsAreEverySlotOfTheAdvisor() {
        List<AvailabilitySlot> slots = List.of(newSlot(1, LocalDateTime.now().plusDays(1)));
        when(availabilitySlotRepository.findByAdvisorId(1L)).thenReturn(slots);

        assertEquals(slots, availabilitySlotService.getMySlots(1L));
    }
}
