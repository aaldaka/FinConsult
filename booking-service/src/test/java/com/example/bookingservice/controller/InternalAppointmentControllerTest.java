package com.example.bookingservice.controller;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.security.InternalKeyFilter;
import com.example.bookingservice.service.AccountStatusService;
import com.example.bookingservice.service.AppointmentService;
import com.example.bookingservice.support.AppointmentTestData;
import com.example.bookingservice.support.BookingLookups;
import com.example.bookingservice.support.SlotTestData;
import com.example.bookingservice.support.TestPeople;
import com.example.bookingservice.support.TestTokens;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). The cascades cancel every active appointment of a person, so every client and
// advisor here is made by the test (TestPeople) and deleted afterwards with the slots and appointments on them; rows
// other tests own are never reached. The user service, the strike client and the stubs are mocks. The repository is a
// spy so one test can make a single appointment fail with a version conflict
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class InternalAppointmentControllerTest {

    private static final String KEY_HEADER = InternalKeyFilter.HEADER;

    @Autowired MockMvc mockMvc;
    @Autowired AppointmentService appointmentService;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoSpyBean AppointmentRepository appointmentRepository;

    @MockitoBean AdvisorClient advisorClient;
    @MockitoBean UserClient userClient;
    @MockitoBean StrikeClient strikeClient;
    @MockitoBean KycCheck kycCheck;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean AccountStatusService accountStatusService;

    @Value("${internal.api-key}") String internalKey;
    @Value("${jwt.secret}") String jwtSecret;

    private SlotTestData data;
    private BookingLookups lookups;
    private AppointmentTestData appointments;
    private TestPeople people;
    private long serviceId;

    private TestPeople.Advisor advisor;
    private TestPeople.Advisor otherAdvisor;
    private long client;
    private long otherClient;
    private final Map<Long, Long> slotOf = new HashMap<>();

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        lookups = new BookingLookups(jdbcTemplate);
        appointments = new AppointmentTestData(jdbcTemplate);
        people = new TestPeople(jdbcTemplate);
        serviceId = lookups.freeServiceTypeId();

        advisor = people.newAdvisor();
        otherAdvisor = people.newAdvisor();
        client = people.newClient();
        otherClient = people.newClient();

        AdvisorInfo advisorInfo = new AdvisorInfo(advisor.id(), advisor.userId(), true, "Leaving Advisor", "Planner", Specialty.GENERAL);
        AdvisorInfo otherInfo = new AdvisorInfo(otherAdvisor.id(), otherAdvisor.userId(), true, "Staying Advisor", "Planner", Specialty.GENERAL);
        when(advisorClient.getById(advisor.id())).thenReturn(advisorInfo);
        when(advisorClient.getById(otherAdvisor.id())).thenReturn(otherInfo);
        when(advisorClient.lookup(any())).thenReturn(Map.of(advisor.id(), advisorInfo, otherAdvisor.id(), otherInfo));
        when(userClient.names(any())).thenReturn(Map.of(client, "Leaving Client", otherClient, "Staying Client"));
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
        people.deleteCreated();
    }

    // helpers

    // an appointment booked through the service, then put in the wanted status
    private long appointment(TestPeople.Advisor of, long clientId, String status) {
        long slotId = data.createSlot(of.id(), SlotStatus.AVAILABLE).getId();
        long id = appointmentService.bookSlot(clientId, slotId, serviceId).getId();
        if (!"PENDING".equals(status)) {
            appointments.setStatus(id, status);
        }
        slotOf.put(id, slotId);
        return id;
    }

    private String slotStatus(long appointmentId) {
        return lookups.statusOfSlot(slotOf.get(appointmentId));
    }

    private String reasonOf(long appointmentId) {
        return jdbcTemplate.queryForObject("SELECT cancellation_reason FROM appointments WHERE id = ?", String.class, appointmentId);
    }

    private ResultActions callWithKey(String url) throws Exception {
        return mockMvc.perform(post(url).header(KEY_HEADER, internalKey));
    }

    private String bearer(long userId, String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, role);
    }

    // the key

    @Test
    void withoutTheKeyOrWithAWrongOneBothEndpointsAre403() throws Exception {
        for (String url : new String[]{"/internal/appointments/cancel-for-advisor/" + advisor.id(),
                "/internal/appointments/cancel-for-client/" + client}) {
            mockMvc.perform(post(url))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Missing or invalid internal key"));
            mockMvc.perform(post(url).header(KEY_HEADER, internalKey + "x")).andExpect(status().isForbidden());
            mockMvc.perform(post(url).header(KEY_HEADER, "")).andExpect(status().isForbidden());
        }
    }

    @Test
    void aUserTokenWithoutTheKeyIs403EvenForAnAdmin() throws Exception {
        long pending = appointment(advisor, client, "PENDING");

        for (String role : new String[]{"ADMIN", "ADVISOR", "CLIENT"}) {
            mockMvc.perform(post("/internal/appointments/cancel-for-advisor/" + advisor.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(advisor.userId(), role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/internal/appointments/cancel-for-client/" + client)
                            .header(HttpHeaders.AUTHORIZATION, bearer(client, role))
                            .header(KEY_HEADER, "wrong"))
                    .andExpect(status().isForbidden());
        }

        assertEquals("PENDING", lookups.statusOfAppointment(pending));
    }

    @Test
    void theKeyOpensNothingOutsideInternal() throws Exception {
        mockMvc.perform(get("/appointments/mine").header(KEY_HEADER, internalKey)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/appointments/no-shows").header(KEY_HEADER, internalKey)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/slots").header(KEY_HEADER, internalKey)).andExpect(status().isUnauthorized());

        // with a client token the key does not add the admin role either
        mockMvc.perform(get("/appointments/no-shows")
                        .header(KEY_HEADER, internalKey)
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    // cancel-for-advisor

    @Test
    void cancellingForAnAdvisorCancelsTheirActiveAppointmentsFreesTheSlotsAndTellsTheClients() throws Exception {
        long pending = appointment(advisor, client, "PENDING");
        long confirmed = appointment(advisor, otherClient, "CONFIRMED");
        long alreadyCancelled = appointment(advisor, client, "CANCELLED");
        long completed = appointment(advisor, client, "COMPLETED");
        long otherAdvisors = appointment(otherAdvisor, client, "PENDING");
        clearInvocations(notificationPublisher);

        callWithKey("/internal/appointments/cancel-for-advisor/" + advisor.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(2))
                .andExpect(jsonPath("$.failed").value(0));

        for (long id : new long[]{pending, confirmed}) {
            assertEquals("CANCELLED", lookups.statusOfAppointment(id));
            assertEquals("Advisor is no longer available", reasonOf(id));
            assertEquals("AVAILABLE", slotStatus(id));
        }
        assertEquals("CANCELLED", lookups.statusOfAppointment(alreadyCancelled));
        assertEquals("COMPLETED", lookups.statusOfAppointment(completed));
        assertEquals("PENDING", lookups.statusOfAppointment(otherAdvisors));
        assertEquals("BOOKED", slotStatus(otherAdvisors));

        verify(notificationPublisher).publish(eq(client), eq(NotificationType.APPOINTMENT_CANCELLED),
                contains("Leaving Advisor cancelled the appointment"), eq(pending));
        verify(notificationPublisher).publish(eq(otherClient), eq(NotificationType.APPOINTMENT_CANCELLED),
                contains("Reason: Advisor is no longer available"), eq(confirmed));
        verify(notificationPublisher, never())
                .publish(anyLong(), eq(NotificationType.APPOINTMENT_CANCELLED), anyString(), eq(otherAdvisors));
        verifyNoInteractions(strikeClient);
    }

    @Test
    void aSecondCallForTheSameAdvisorCancelsNothing() throws Exception {
        appointment(advisor, client, "PENDING");

        callWithKey("/internal/appointments/cancel-for-advisor/" + advisor.id()).andExpect(jsonPath("$.cancelled").value(1));
        callWithKey("/internal/appointments/cancel-for-advisor/" + advisor.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }

    // cancel-for-client

    @Test
    void cancellingForAClientCancelsTheirActiveAppointmentsFreesTheSlotsAndTellsTheAdvisors() throws Exception {
        long withAdvisor = appointment(advisor, client, "PENDING");
        long withOther = appointment(otherAdvisor, client, "CONFIRMED");
        long noShow = appointment(advisor, client, "NO_SHOW");
        long otherClients = appointment(advisor, otherClient, "CONFIRMED");
        clearInvocations(notificationPublisher);

        callWithKey("/internal/appointments/cancel-for-client/" + client)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(2))
                .andExpect(jsonPath("$.failed").value(0));

        for (long id : new long[]{withAdvisor, withOther}) {
            assertEquals("CANCELLED", lookups.statusOfAppointment(id));
            assertEquals("Client account was deactivated", reasonOf(id));
            assertEquals("AVAILABLE", slotStatus(id));
        }
        assertEquals("NO_SHOW", lookups.statusOfAppointment(noShow));
        assertEquals("CONFIRMED", lookups.statusOfAppointment(otherClients));
        assertEquals("BOOKED", slotStatus(otherClients));

        verify(notificationPublisher).publish(eq(advisor.userId()), eq(NotificationType.APPOINTMENT_CANCELLED),
                contains("Your appointment with Leaving Client"), eq(withAdvisor));
        verify(notificationPublisher).publish(eq(otherAdvisor.userId()), eq(NotificationType.APPOINTMENT_CANCELLED),
                contains("Reason: Client account was deactivated"), eq(withOther));
        verify(notificationPublisher, never())
                .publish(anyLong(), eq(NotificationType.APPOINTMENT_CANCELLED), anyString(), eq(otherClients));
        verifyNoInteractions(strikeClient);
    }

    @Test
    void aSecondCallForTheSameClientCancelsNothing() throws Exception {
        appointment(advisor, client, "CONFIRMED");

        callWithKey("/internal/appointments/cancel-for-client/" + client).andExpect(jsonPath("$.cancelled").value(1));
        callWithKey("/internal/appointments/cancel-for-client/" + client)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void someoneWithNoAppointmentsGetsZeroAndZero() throws Exception {
        callWithKey("/internal/appointments/cancel-for-client/" + otherClient)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }

    // the optional body

    private ResultActions callWithKeyAndBody(String url, String body) throws Exception {
        return mockMvc.perform(post(url).header(KEY_HEADER, internalKey).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void aReasonInTheBodyIsUsedForAnAdvisorsCancellationsAndTheClientsMessage() throws Exception {
        long pending = appointment(advisor, client, "PENDING");
        clearInvocations(notificationPublisher);

        callWithKeyAndBody("/internal/appointments/cancel-for-advisor/" + advisor.id(), "{\"reason\": \"Left the company\", \"adminId\": 7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(1));

        assertEquals("Left the company", reasonOf(pending));
        verify(notificationPublisher).publish(eq(client), eq(NotificationType.APPOINTMENT_CANCELLED),
                contains("Reason: Left the company"), eq(pending));
    }

    @Test
    void aReasonInTheBodyIsUsedForAClientsCancellationsAndTheAdvisorsMessage() throws Exception {
        long confirmed = appointment(advisor, client, "CONFIRMED");
        clearInvocations(notificationPublisher);

        callWithKeyAndBody("/internal/appointments/cancel-for-client/" + client, "{\"reason\": \"Fraud review\", \"adminId\": 7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(1));

        assertEquals("Fraud review", reasonOf(confirmed));
        verify(notificationPublisher).publish(eq(advisor.userId()), eq(NotificationType.APPOINTMENT_CANCELLED),
                contains("Reason: Fraud review"), eq(confirmed));
    }

    @Test
    void withoutABodyBothEndpointsUseTheDefaultTexts() throws Exception {
        long forAdvisor = appointment(advisor, otherClient, "PENDING");
        long forClient = appointment(otherAdvisor, client, "PENDING");

        callWithKey("/internal/appointments/cancel-for-advisor/" + advisor.id()).andExpect(status().isOk());
        callWithKey("/internal/appointments/cancel-for-client/" + client).andExpect(status().isOk());

        assertEquals("Advisor is no longer available", reasonOf(forAdvisor));
        assertEquals("Client account was deactivated", reasonOf(forClient));
    }

    @Test
    void aBodyWithANullOrBlankReasonAlsoUsesTheDefaultTexts() throws Exception {
        long forAdvisor = appointment(advisor, otherClient, "PENDING");
        long forClient = appointment(otherAdvisor, client, "PENDING");

        callWithKeyAndBody("/internal/appointments/cancel-for-advisor/" + advisor.id(), "{\"reason\": \"   \", \"adminId\": null}")
                .andExpect(status().isOk());
        callWithKeyAndBody("/internal/appointments/cancel-for-client/" + client, "{}")
                .andExpect(status().isOk());

        assertEquals("Advisor is no longer available", reasonOf(forAdvisor));
        assertEquals("Client account was deactivated", reasonOf(forClient));
    }

    @Test
    void aReasonLongerThanTheColumnIs400AndCancelsNothing() throws Exception {
        long pending = appointment(advisor, client, "PENDING");

        callWithKeyAndBody("/internal/appointments/cancel-for-advisor/" + advisor.id(), "{\"reason\": \"" + "x".repeat(256) + "\"}")
                .andExpect(status().isBadRequest());

        assertEquals("PENDING", lookups.statusOfAppointment(pending));
    }

    // one conflict does not block the rest

    @Test
    void aConflictOnOneAppointmentIsCountedAsFailedAndTheOthersStillCancel() throws Exception {
        long first = appointment(advisor, client, "PENDING");
        long conflicting = appointment(advisor, otherClient, "CONFIRMED");
        long last = appointment(advisor, client, "PENDING");
        doThrow(new ObjectOptimisticLockingFailureException(Appointment.class, conflicting))
                .when(appointmentRepository).save(argThat((Appointment a) -> a != null && a.getId() != null && a.getId() == conflicting));

        callWithKey("/internal/appointments/cancel-for-advisor/" + advisor.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(2))
                .andExpect(jsonPath("$.failed").value(1));

        assertEquals("CANCELLED", lookups.statusOfAppointment(first));
        assertEquals("CANCELLED", lookups.statusOfAppointment(last));
        assertEquals("CONFIRMED", lookups.statusOfAppointment(conflicting));
        assertEquals("BOOKED", slotStatus(conflicting), "the failed one's slot stays booked, its transaction rolled back");
        verify(notificationPublisher, never())
                .publish(anyLong(), eq(NotificationType.APPOINTMENT_CANCELLED), anyString(), eq(conflicting));
        verifyNoInteractions(strikeClient);
    }
}
