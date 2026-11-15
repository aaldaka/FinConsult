package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.StrikeResult;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.integration.EventPublisher;
import com.example.bookingservice.integration.KafkaAuditRecorder;
import com.example.bookingservice.integration.KafkaNotificationPublisher;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.ServiceType;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.repository.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// the booking flows with the real Kafka publishers in place of the mocks: repositories and clients are mocks, the
// KafkaTemplate is a mock that records what was sent. Each test checks the events one flow sends, in order, as
// "topic key type action-or-notificationType", plus the ids in the payloads
@ExtendWith(MockitoExtension.class)
class AppointmentEventsTest {

    private static final long CLIENT = 10L;
    private static final long ADVISOR = 1L;
    private static final long ADVISOR_USER = 101L;
    private static final long ADMIN = 900L;
    private static final long SLOT = 1L;
    private static final long SERVICE = 5L;
    private static final long APPOINTMENT = 77L;

    @Mock AppointmentRepository appointmentRepository;
    @Mock AvailabilitySlotRepository slotRepository;
    @Mock ServiceTypeRepository serviceTypeRepository;
    @Mock AdvisorClient advisorClient;
    @Mock UserClient userClient;
    @Mock StrikeClient strikeClient;
    @Mock KycCheck kycCheck;
    @Mock AccountStatusService accountStatusService;
    @Mock PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        EventPublisher events = new EventPublisher(kafkaTemplate, objectMapper);
        service = new AppointmentService(appointmentRepository, slotRepository, serviceTypeRepository, advisorClient, userClient,
                strikeClient, kycCheck, new KafkaNotificationPublisher(events), new KafkaAuditRecorder(events),
                accountStatusService, transactionManager);
    }

    // helpers

    private record Sent(String topic, String key, JsonNode event) {
        String summary() {
            JsonNode payload = event.get("payload");
            String what = payload.has("action") ? payload.get("action").asString() : payload.get("notificationType").asString();
            return topic + " " + key + " " + event.get("type").asString() + " " + what;
        }

        JsonNode payload() {
            return event.get("payload");
        }
    }

    private List<Sent> sent() {
        ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, atLeast(1)).send(topics.capture(), keys.capture(), values.capture());
        List<Sent> sent = new ArrayList<>();
        for (int i = 0; i < topics.getAllValues().size(); i++) {
            sent.add(new Sent(topics.getAllValues().get(i), keys.getAllValues().get(i), objectMapper.readTree(values.getAllValues().get(i))));
        }
        return sent;
    }

    private List<String> summaries(List<Sent> sent) {
        return sent.stream().map(Sent::summary).toList();
    }

    private AdvisorInfo advisor() {
        return new AdvisorInfo(ADVISOR, ADVISOR_USER, true, "Advisor Name", "Planner", Specialty.GENERAL);
    }

    private AvailabilitySlot slot(LocalDateTime start, SlotStatus status) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setId(SLOT);
        slot.setAdvisorId(ADVISOR);
        slot.setStartTime(start);
        slot.setEndTime(start.plusHours(1));
        slot.setStatus(status);
        return slot;
    }

    private ServiceType freeService() {
        ServiceType type = new ServiceType();
        type.setId(SERVICE);
        type.setName("Retirement review");
        type.setRequiresKyc(false);
        type.setActive(true);
        return type;
    }

    private void existing(AppointmentStatus status, LocalDateTime start) {
        Appointment appointment = new Appointment();
        appointment.setId(APPOINTMENT);
        appointment.setClientId(CLIENT);
        appointment.setAdvisorId(ADVISOR);
        appointment.setSlot(slot(start, SlotStatus.BOOKED));
        appointment.setServiceType(freeService());
        appointment.setStatus(status);
        lenient().when(appointmentRepository.findAdvisorIdById(APPOINTMENT)).thenReturn(Optional.of(ADVISOR));
        lenient().when(advisorClient.getById(ADVISOR)).thenReturn(advisor());
        lenient().when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor());
        when(appointmentRepository.findById(APPOINTMENT)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void assertNotification(Sent sent, long userId) {
        assertEquals(userId, sent.payload().get("userId").asLong());
        assertEquals(APPOINTMENT, sent.payload().get("relatedAppointmentId").asLong());
    }

    private void assertAudit(Sent sent, long actorId, long entityId) {
        assertEquals(actorId, sent.payload().get("actorId").asLong());
        assertEquals(entityId, sent.payload().get("entityId").asLong());
    }

    // the flows

    @Test
    void bookingTellsTheAdvisorAndAuditsTheAppointment() {
        AvailabilitySlot slot = slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE);
        when(slotRepository.findAdvisorIdById(SLOT)).thenReturn(Optional.of(ADVISOR));
        when(advisorClient.getById(ADVISOR)).thenReturn(advisor());
        when(slotRepository.findByIdForUpdate(SLOT)).thenReturn(Optional.of(slot));
        when(serviceTypeRepository.findById(SERVICE)).thenReturn(Optional.of(freeService()));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            saved.setId(APPOINTMENT);
            return saved;
        });

        service.bookSlot(CLIENT, SLOT, SERVICE);

        List<Sent> sent = sent();
        assertEquals(List.of(
                "notification-events 101 NOTIFICATION APPOINTMENT_BOOKED",
                "audit-events Appointment:77 AUDIT APPOINTMENT_BOOKED"), summaries(sent));
        assertNotification(sent.get(0), ADVISOR_USER);
        assertAudit(sent.get(1), CLIENT, APPOINTMENT);
    }

    @Test
    void confirmingTellsTheClientAndAuditsTheAppointment() {
        existing(AppointmentStatus.PENDING, LocalDateTime.now().plusDays(1));

        service.confirmAppointment(ADVISOR_USER, APPOINTMENT);

        List<Sent> sent = sent();
        assertEquals(List.of(
                "notification-events 10 NOTIFICATION APPOINTMENT_CONFIRMED",
                "audit-events Appointment:77 AUDIT APPOINTMENT_CONFIRMED"), summaries(sent));
        assertNotification(sent.get(0), CLIENT);
        assertAudit(sent.get(1), ADVISOR_USER, APPOINTMENT);
    }

    @Test
    void aClientCancellingEarlyAuditsItAndTellsTheAdvisor() {
        existing(AppointmentStatus.CONFIRMED, LocalDateTime.now().plusDays(3));

        service.cancelAppointment(APPOINTMENT, CLIENT, "Travelling");

        List<Sent> sent = sent();
        assertEquals(List.of(
                "audit-events Appointment:77 AUDIT APPOINTMENT_CANCELLED",
                "notification-events 101 NOTIFICATION APPOINTMENT_CANCELLED"), summaries(sent));
        assertAudit(sent.get(0), CLIENT, APPOINTMENT);
        assertEquals("Travelling", sent.get(0).payload().get("details").asString());
        assertNotification(sent.get(1), ADVISOR_USER);
    }

    @Test
    void aNoShowThatBlacklistsAuditsBothAndTellsTheClient() {
        existing(AppointmentStatus.CONFIRMED, LocalDateTime.now().minusHours(2));
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(3, true));

        service.markNoShow(ADVISOR_USER, APPOINTMENT);

        List<Sent> sent = sent();
        assertEquals(List.of(
                "audit-events Appointment:77 AUDIT APPOINTMENT_NO_SHOW",
                "audit-events User:10 AUDIT CLIENT_BLACKLISTED",
                "notification-events 10 NOTIFICATION APPOINTMENT_NO_SHOW"), summaries(sent));
        assertAudit(sent.get(0), ADVISOR_USER, APPOINTMENT);
        assertAudit(sent.get(1), CLIENT, CLIENT);
        assertEquals("User", sent.get(1).payload().get("entityType").asString());
        assertNotification(sent.get(2), CLIENT);
    }

    @Test
    void excusingANoShowAuditsItAndTellsTheClient() {
        existing(AppointmentStatus.NO_SHOW, LocalDateTime.now().minusDays(1));
        when(strikeClient.removeStrike(CLIENT)).thenReturn(new StrikeResult(1, false));

        service.excuseNoShow(ADMIN, APPOINTMENT, "Hospital note");

        List<Sent> sent = sent();
        assertEquals(List.of(
                "audit-events Appointment:77 AUDIT NO_SHOW_EXCUSED",
                "notification-events 10 NOTIFICATION APPOINTMENT_NO_SHOW"), summaries(sent));
        assertAudit(sent.get(0), ADMIN, APPOINTMENT);
        assertNotification(sent.get(1), CLIENT);
    }

    @Test
    void aBrokerThatIsDownDoesNotFailTheFlow() {
        existing(AppointmentStatus.PENDING, LocalDateTime.now().plusDays(1));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("broker not available"));

        Appointment confirmed = service.confirmAppointment(ADVISOR_USER, APPOINTMENT);

        assertEquals(AppointmentStatus.CONFIRMED, confirmed.getStatus());
    }
}
