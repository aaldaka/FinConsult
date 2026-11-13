package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.support.AppointmentTestData;
import com.example.bookingservice.support.BookingLookups;
import com.example.bookingservice.support.SlotTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

// runs on finconsult_test (TEST_DB_URL), no @Transactional on the class. The job is called directly, so the test does not
// wait for the schedule. It also completes other finished confirmed appointments in the database, which is what it is for,
// so the test only looks at the rows it created
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AppointmentCompletionJobDatabaseTest {

    @Autowired AppointmentCompletionJob job;
    @Autowired AppointmentService appointmentService;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean AdvisorClient advisorClient;
    @MockitoBean UserClient userClient;
    @MockitoBean StrikeClient strikeClient;
    @MockitoBean KycCheck kycCheck;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean AccountStatusService accountStatusService;

    private SlotTestData data;
    private BookingLookups lookups;
    private AppointmentTestData appointments;
    private long advisorId;
    private long clientId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        lookups = new BookingLookups(jdbcTemplate);
        appointments = new AppointmentTestData(jdbcTemplate);
        advisorId = data.anAdvisorId();
        clientId = lookups.aClientId();
        when(advisorClient.getById(advisorId))
                .thenReturn(new AdvisorInfo(advisorId, lookups.userIdOfAdvisor(advisorId), true, "Job Advisor", "Planner", Specialty.GENERAL));
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    // an appointment with the given status whose slot ends the given number of minutes from now (negative: already over)
    private long appointment(String status, long slotEndsInMinutes) {
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        long appointmentId = appointmentService.bookSlot(clientId, slotId, lookups.freeServiceTypeId()).getId();
        LocalDateTime end = LocalDateTime.now().plusMinutes(slotEndsInMinutes);
        appointments.moveSlot(slotId, end.minusHours(1), end);
        appointments.setStatus(appointmentId, status);
        return appointmentId;
    }

    @Test
    void aConfirmedAppointmentWhoseSlotHasEndedIsCompleted() {
        long finished = appointment("CONFIRMED", -10);

        job.completeFinishedAppointments();

        assertEquals("COMPLETED", lookups.statusOfAppointment(finished));
    }

    @Test
    void aConfirmedAppointmentWhoseSlotIsStillRunningOrAheadIsLeftAlone() {
        long running = appointment("CONFIRMED", 30);
        long ahead = appointment("CONFIRMED", 24 * 60);

        job.completeFinishedAppointments();

        assertEquals("CONFIRMED", lookups.statusOfAppointment(running));
        assertEquals("CONFIRMED", lookups.statusOfAppointment(ahead));
    }

    @Test
    void onlyConfirmedAppointmentsAreTouched() {
        long pending = appointment("PENDING", -10);
        long cancelled = appointment("CANCELLED", -10);
        long noShow = appointment("NO_SHOW", -10);

        job.completeFinishedAppointments();

        assertEquals("PENDING", lookups.statusOfAppointment(pending));
        assertEquals("CANCELLED", lookups.statusOfAppointment(cancelled));
        assertEquals("NO_SHOW", lookups.statusOfAppointment(noShow));
    }

    @Test
    void runningItAgainChangesNothing() {
        long finished = appointment("CONFIRMED", -10);
        long ahead = appointment("CONFIRMED", 24 * 60);

        job.completeFinishedAppointments();
        job.completeFinishedAppointments();

        assertEquals("COMPLETED", lookups.statusOfAppointment(finished));
        assertEquals("CONFIRMED", lookups.statusOfAppointment(ahead));
    }
}
