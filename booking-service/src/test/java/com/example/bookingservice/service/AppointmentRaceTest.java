package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.support.BookingLookups;
import com.example.bookingservice.support.RaceRunner;
import com.example.bookingservice.support.RaceRunner.Result;
import com.example.bookingservice.support.SlotTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

// runs on finconsult_test (TEST_DB_URL). Do NOT put @Transactional on this class: every thread needs its own
// committed transaction. The advisor, the clients and the service type are existing rows found with read-only queries,
// the user service and the stubs are mocks, and only the slots (and their appointments) the test creates are deleted
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AppointmentRaceTest {

    @Autowired AppointmentService appointmentService;
    @Autowired AvailabilitySlotService availabilitySlotService;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean AdvisorClient advisorClient;
    @MockitoBean UserClient userClient;
    @MockitoBean StrikeClient strikeClient;
    @MockitoBean KycCheck kycCheck;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean AccountStatusService accountStatusService; // the account is always fine here, the races are about the database

    private SlotTestData data;
    private BookingLookups lookups;
    private long advisorId;
    private long advisorUserId;
    private long serviceId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        lookups = new BookingLookups(jdbcTemplate);
        advisorId = data.anAdvisorId();
        advisorUserId = lookups.userIdOfAdvisor(advisorId);
        serviceId = lookups.freeServiceTypeId();
        AdvisorInfo info = new AdvisorInfo(advisorId, advisorUserId, true, "Race Advisor", "Planner", Specialty.GENERAL);
        when(advisorClient.getById(advisorId)).thenReturn(info);
        when(advisorClient.getByUserId(advisorUserId)).thenReturn(info);
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    // two clients press "Book" on the same slot at the same moment
    @RepeatedTest(3)
    void twoClientsOneSlot() throws Exception {
        AvailabilitySlot slot = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        List<Long> clients = lookups.clientIds(2);

        Result result = RaceRunner.runAtOnce(List.of(
                () -> appointmentService.bookSlot(clients.get(0), slot.getId(), serviceId),
                () -> appointmentService.bookSlot(clients.get(1), slot.getId(), serviceId)));

        assertEquals(0, result.other(), "no unexpected errors");
        assertEquals(1, result.successes(), "exactly one booking should win");
        assertEquals(1, result.refused(), "the other one must be refused");
        assertEquals(1, lookups.activeAppointmentsOnSlot(slot.getId()), "one active appointment for the slot");
        assertEquals("BOOKED", lookups.statusOfSlot(slot.getId()));
    }

    // eight clients at once: one winner, seven refused
    @RepeatedTest(5)
    void eightClientsOneSlot() throws Exception {
        AvailabilitySlot slot = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        List<Long> clients = lookups.clientIds(8);

        List<Runnable> tasks = new ArrayList<>();
        for (Long client : clients) {
            tasks.add(() -> appointmentService.bookSlot(client, slot.getId(), serviceId));
        }

        Result result = RaceRunner.runAtOnce(tasks);

        assertEquals(0, result.other());
        assertEquals(1, result.successes());
        assertEquals(7, result.refused());
        assertEquals(1, lookups.activeAppointmentsOnSlot(slot.getId()));
    }

    // the advisor deletes the slot while a client books it: only one of the two may win
    @RepeatedTest(10)
    void deleteSlotWhileBooking() throws Exception {
        AvailabilitySlot slot = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        long client = lookups.aClientId();

        Result result = RaceRunner.runAtOnce(List.of(
                () -> appointmentService.bookSlot(client, slot.getId(), serviceId),
                () -> availabilitySlotService.deleteSlot(advisorId, slot.getId())));

        boolean slotStillExists = slotRepository.findById(slot.getId()).isPresent();

        assertEquals(0, result.other());
        assertEquals(1, result.successes(), "either the booking or the delete wins, never both");
        assertEquals(1, result.refused());
        // booked slot -> one appointment and the slot exists; deleted slot -> no appointment
        assertEquals(slotStillExists ? 1 : 0, lookups.activeAppointmentsOnSlot(slot.getId()));
    }

    // the advisor confirms while the client cancels (@Version on Appointment)
    @RepeatedTest(15)
    void confirmWhileCancelling() throws Exception {
        AvailabilitySlot slot = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        long client = lookups.aClientId();
        Appointment appointment = appointmentService.bookSlot(client, slot.getId(), serviceId);

        Result result = RaceRunner.runAtOnce(List.of(
                () -> appointmentService.confirmAppointment(advisorUserId, appointment.getId()),
                () -> appointmentService.cancelAppointment(appointment.getId(), client, "changed my mind")));

        String status = lookups.statusOfAppointment(appointment.getId());
        String slotStatus = lookups.statusOfSlot(slot.getId());

        assertEquals(0, result.other(), "no unexpected errors");
        assertTrue(status.equals("CONFIRMED") || status.equals("CANCELLED"), "final status must be one of the two, was " + status);
        // the slot must always match the appointment: cancelled -> free again, confirmed -> still booked
        assertEquals(status.equals("CANCELLED") ? "AVAILABLE" : "BOOKED", slotStatus);
    }
}
