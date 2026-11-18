package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.exception.InvalidRequest;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.exception.SlotUnavailable;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.ServiceType;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.model.enums.DocumentType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.repository.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// no Spring and no database: the KYC check is asked before the booking transaction opens, and its answer is used
// inside it at the same point and with the same messages as the original monolith
@ExtendWith(MockitoExtension.class)
class KycBookingServiceTest {

    private static final long CLIENT = 10L;
    private static final long ADVISOR = 1L;
    private static final long SLOT = 1L;
    private static final long SERVICE = 5L;

    @Mock AppointmentRepository appointmentRepository;
    @Mock AvailabilitySlotRepository slotRepository;
    @Mock ServiceTypeRepository serviceTypeRepository;
    @Mock AdvisorClient advisorClient;
    @Mock UserClient userClient;
    @Mock StrikeClient strikeClient;
    @Mock KycCheck kycCheck;
    @Mock NotificationPublisher notifications;
    @Mock AuditRecorder audit;
    @Mock AccountStatusService accountStatusService;
    @Mock PlatformTransactionManager transactionManager;

    private AppointmentService service;
    private AvailabilitySlot slot;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, slotRepository, serviceTypeRepository, advisorClient, userClient,
                strikeClient, kycCheck, notifications, audit, accountStatusService, transactionManager);
        slot = new AvailabilitySlot();
        slot.setId(SLOT);
        slot.setAdvisorId(ADVISOR);
        slot.setStartTime(LocalDateTime.now().plusDays(1));
        slot.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        slot.setStatus(SlotStatus.AVAILABLE);
        lenient().when(slotRepository.findAdvisorIdById(SLOT)).thenReturn(Optional.of(ADVISOR));
        lenient().when(advisorClient.getById(ADVISOR)).thenReturn(new AdvisorInfo(ADVISOR, 101L, true, "Advisor", "Planner", Specialty.GENERAL));
        lenient().when(slotRepository.findByIdForUpdate(SLOT)).thenReturn(Optional.of(slot));
        lenient().when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            saved.setId(77L);
            return saved;
        });
    }

    private ServiceType service(boolean requiresKyc, DocumentType type) {
        ServiceType serviceType = new ServiceType();
        serviceType.setId(SERVICE);
        serviceType.setName("Investment review");
        serviceType.setActive(true);
        serviceType.setRequiresKyc(requiresKyc);
        serviceType.setRequiredDocumentType(type);
        when(serviceTypeRepository.findById(SERVICE)).thenReturn(Optional.of(serviceType));
        return serviceType;
    }

    @Test
    void anApprovedClientBooksAndTheCheckRunsBeforeTheTransaction() {
        service(true, DocumentType.INCOME_PROOF);
        when(kycCheck.isApproved(CLIENT, DocumentType.INCOME_PROOF)).thenReturn(true);

        Appointment booked = service.bookSlot(CLIENT, SLOT, SERVICE);

        assertEquals(AppointmentStatus.PENDING, booked.getStatus());
        InOrder order = inOrder(advisorClient, kycCheck, transactionManager);
        order.verify(advisorClient).getById(ADVISOR);
        order.verify(kycCheck).isApproved(CLIENT, DocumentType.INCOME_PROOF);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void aClientWithoutTheDocumentGetsTheMainAppsMessage() {
        service(true, DocumentType.INCOME_PROOF);
        when(kycCheck.isApproved(CLIENT, DocumentType.INCOME_PROOF)).thenReturn(false);

        assertEquals("This service requires an approved INCOME_PROOF document",
                assertThrows(InvalidRequest.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
    }

    @Test
    void withoutARequiredTypeAnyApprovedDocumentIsAskedForWithTheMainAppsMessage() {
        service(true, null);
        when(kycCheck.isApproved(CLIENT, null)).thenReturn(false);

        assertEquals("This service requires an approved KYC document",
                assertThrows(InvalidRequest.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());
        verify(kycCheck).isApproved(CLIENT, null);
    }

    @Test
    void aDocumentServiceThatIsDownIs503AndNoTransactionOpens() {
        service(true, DocumentType.PASSPORT);
        when(kycCheck.isApproved(CLIENT, DocumentType.PASSPORT))
                .thenThrow(new ServiceUnavailable("The document service is not available, please try again later"));

        assertThrows(ServiceUnavailable.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE));

        verifyNoInteractions(transactionManager);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void aServiceWithoutKycNeverAsks() {
        service(false, null);

        service.bookSlot(CLIENT, SLOT, SERVICE);

        verifyNoInteractions(kycCheck);
    }

    @Test
    void theOtherChecksKeepTheirOrderAndComeBeforeTheKycAnswer() {
        service(true, DocumentType.PASSPORT);
        when(kycCheck.isApproved(CLIENT, DocumentType.PASSPORT)).thenReturn(false);
        slot.setStatus(SlotStatus.BOOKED);

        assertThrows(SlotUnavailable.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE), "a taken slot is still a 409 first");
    }

    @Test
    void aRequirementThatChangedAfterTheAnswerFailsClosed() {
        ServiceType serviceType = service(true, null);
        when(kycCheck.isApproved(CLIENT, null)).thenReturn(true); // asked for "any document"
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> {
            serviceType.setRequiredDocumentType(DocumentType.PASSPORT); // an admin tightens the rule meanwhile
            return null;
        });

        assertEquals("This service requires an approved PASSPORT document",
                assertThrows(InvalidRequest.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());
        verify(kycCheck, never()).isApproved(anyLong(), any(DocumentType.class));
    }
}
