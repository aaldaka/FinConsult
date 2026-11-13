package com.example.bookingservice.controller;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.UserInfo;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.support.AppointmentTestData;
import com.example.bookingservice.support.BookingLookups;
import com.example.bookingservice.support.SlotTestData;
import com.example.bookingservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). Someone else changing the appointment between the read and the save is a
// version conflict; the repository is a spy that throws it on the save, so the 409 does not depend on thread timing.
// It is its own class so the spy does not change the context of the other controller tests
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NoShowConflictControllerTest {

    private static final long ADMIN = 910_008L;
    private static final String CONFLICT = "This record was changed by someone else. Please refresh and try again";

    @Autowired MockMvc mockMvc;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoSpyBean AppointmentRepository appointmentRepository;

    @MockitoBean AdvisorClient advisorClient;
    @MockitoBean UserClient userClient;
    @MockitoBean StrikeClient strikeClient;
    @MockitoBean KycCheck kycCheck;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;

    @Value("${jwt.secret}") String jwtSecret;

    private SlotTestData data;
    private BookingLookups lookups;
    private AppointmentTestData appointments;
    private long advisorId;
    private long advisorUserId;
    private long clientId;
    private long serviceId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        lookups = new BookingLookups(jdbcTemplate);
        appointments = new AppointmentTestData(jdbcTemplate);
        advisorId = data.anAdvisorId();
        advisorUserId = lookups.userIdOfAdvisor(advisorId);
        clientId = lookups.aClientId();
        serviceId = lookups.freeServiceTypeId();

        AdvisorInfo advisor = new AdvisorInfo(advisorId, advisorUserId, true, "Test Advisor", "Planner", Specialty.GENERAL);
        when(advisorClient.getById(advisorId)).thenReturn(advisor);
        when(advisorClient.getByUserId(advisorUserId)).thenReturn(advisor);
        when(advisorClient.lookup(any())).thenReturn(Map.of(advisorId, advisor));
        when(userClient.names(any())).thenReturn(Map.of(clientId, "Test Client"));
        for (long id : new long[]{clientId, advisorUserId, ADMIN}) {
            when(userClient.getById(id)).thenReturn(new UserInfo(id, "u" + id + "@test.com", "CLIENT", "ACTIVE", false, 0, "User " + id));
        }
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    private String bearer(long userId, String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, role);
    }

    // a confirmed appointment on a slot that started 30 minutes ago, made through the api before the spy is told to throw
    private long overdueConfirmed() throws Exception {
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        String response = mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypeId\": " + serviceId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long appointmentId = ((Number) JsonPath.read(response, "$.id")).longValue();
        mockMvc.perform(patch("/appointments/" + appointmentId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR"))).andExpect(status().isOk());
        appointments.startedHalfAnHourAgo(slotId);
        return appointmentId;
    }

    @Test
    void aVersionConflictOnANoShowIs409AndNoStrikeIsAdded() throws Exception {
        long appointmentId = overdueConfirmed();
        doThrow(new ObjectOptimisticLockingFailureException(Appointment.class, appointmentId))
                .when(appointmentRepository).save(any(Appointment.class));

        mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CONFLICT));

        assertEquals("CONFIRMED", lookups.statusOfAppointment(appointmentId));
        verifyNoInteractions(strikeClient);
    }

    @Test
    void aVersionConflictOnAnExcuseIs409AndNoStrikeIsRemoved() throws Exception {
        long appointmentId = overdueConfirmed();
        appointments.setStatus(appointmentId, "NO_SHOW");
        doThrow(new ObjectOptimisticLockingFailureException(Appointment.class, appointmentId))
                .when(appointmentRepository).save(any(Appointment.class));

        mockMvc.perform(patch("/appointments/" + appointmentId + "/excuse")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"mistake\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CONFLICT));

        assertFalse(appointments.isExcused(appointmentId));
        verifyNoInteractions(strikeClient);
    }
}
