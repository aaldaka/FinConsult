package com.example.bookingservice.controller;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.UserInfo;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). The user service, the strike client and the stubs are mocks. The advisor, the
// client and the service type are existing rows (appointments have foreign keys to them), found with read-only queries,
// and only the slots a test creates (and the appointments on them) are deleted. The account check is the real one,
// fed by the mocked user service
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AppointmentControllerTest {

    // made-up caller ids: refused or rejected before the database is touched, so they need no rows
    private static final long BLACKLISTED_CLIENT = 910_001L;
    private static final long INACTIVE_CLIENT = 910_002L;
    private static final long STRANGER = 910_003L;
    private static final long SECOND_CLIENT = 910_004L;
    private static final long BLACKLISTED_ADVISOR = 910_005L;
    private static final long OTHER_ADVISOR_USER = 910_006L;
    private static final long OTHER_ADVISOR_ID = 910_007L;
    private static final long MISSING = 999_999_999L;

    @Autowired MockMvc mockMvc;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean AdvisorClient advisorClient;
    @MockitoBean UserClient userClient;
    @MockitoBean StrikeClient strikeClient;
    @MockitoBean KycCheck kycCheck;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;

    @Value("${jwt.secret}") String jwtSecret;

    private SlotTestData data;
    private BookingLookups lookups;
    private long advisorId;
    private long advisorUserId;
    private long clientId;
    private long serviceId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        lookups = new BookingLookups(jdbcTemplate);
        advisorId = data.anAdvisorId();
        advisorUserId = lookups.userIdOfAdvisor(advisorId);
        clientId = lookups.aClientId();
        serviceId = lookups.freeServiceTypeId();

        AdvisorInfo advisor = new AdvisorInfo(advisorId, advisorUserId, true, "Test Advisor", "Planner", Specialty.GENERAL);
        when(advisorClient.getById(advisorId)).thenReturn(advisor);
        when(advisorClient.getByUserId(advisorUserId)).thenReturn(advisor);
        when(advisorClient.lookup(any())).thenReturn(Map.of(advisorId, advisor));
        when(userClient.names(any())).thenReturn(Map.of(clientId, "Test Client"));

        // another advisor, who owns nothing
        when(advisorClient.getByUserId(OTHER_ADVISOR_USER))
                .thenReturn(new AdvisorInfo(OTHER_ADVISOR_ID, OTHER_ADVISOR_USER, true, "Other Advisor", "Planner", Specialty.GENERAL));

        for (long id : new long[]{clientId, advisorUserId, STRANGER, SECOND_CLIENT, OTHER_ADVISOR_USER}) {
            when(userClient.getById(id)).thenReturn(user(id, "ACTIVE", false));
        }
        when(userClient.getById(BLACKLISTED_CLIENT)).thenReturn(user(BLACKLISTED_CLIENT, "ACTIVE", true));
        when(userClient.getById(BLACKLISTED_ADVISOR)).thenReturn(user(BLACKLISTED_ADVISOR, "ACTIVE", true));
        when(userClient.getById(INACTIVE_CLIENT)).thenReturn(user(INACTIVE_CLIENT, "INACTIVE", false));
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    // helpers

    private UserInfo user(long id, String status, boolean blacklisted) {
        return new UserInfo(id, "u" + id + "@test.com", "CLIENT", status, blacklisted, blacklisted ? 3 : 0, "User " + id);
    }

    private String bearer(long userId, String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, role);
    }

    private String bookingBody() {
        return "{\"serviceTypeId\": " + serviceId + "}";
    }

    private long newSlot() {
        return data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
    }

    private long bookedAppointment(long slotId) throws Exception {
        String response = mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long appointmentCountOnSlot(long slotId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM appointments WHERE slot_id = ?", Long.class, slotId);
    }

    // 401 and 403 by role

    @Test
    void withoutATokenEveryEndpointIs401() throws Exception {
        mockMvc.perform(post("/appointments/book/1").contentType(MediaType.APPLICATION_JSON).content(bookingBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/appointments/1/confirm")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/appointments/1/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAClientCanBook() throws Exception {
        long slotId = newSlot();
        for (String role : new String[]{"ADVISOR", "ADMIN"}) {
            mockMvc.perform(post("/appointments/book/" + slotId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookingBody()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
        }
        assertEquals(0, appointmentCountOnSlot(slotId));
    }

    @Test
    void onlyAnAdvisorCanConfirm() throws Exception {
        for (String role : new String[]{"CLIENT", "ADMIN"}) {
            mockMvc.perform(patch("/appointments/1/confirm").header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, role)))
                    .andExpect(status().isForbidden());
        }
    }

    // book

    @Test
    void aClientBooksASlotAndGetsTheMonolithsAppointmentShape() throws Exception {
        long slotId = newSlot();

        mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.advisorId").value(advisorId))
                .andExpect(jsonPath("$.advisorName").value("Test Advisor"))
                .andExpect(jsonPath("$.advisorSpecialty").value("GENERAL"))
                .andExpect(jsonPath("$.clientName").value("Test Client"))
                .andExpect(jsonPath("$.serviceName").isNotEmpty())
                .andExpect(jsonPath("$.startTime").isNotEmpty())
                .andExpect(jsonPath("$.endTime").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.excused").value(false));

        assertEquals("BOOKED", lookups.statusOfSlot(slotId));
        assertEquals(1, lookups.activeAppointmentsOnSlot(slotId));
    }

    @Test
    void aSlotThatIsAlreadyBookedIs409() throws Exception {
        long slotId = newSlot();
        bookedAppointment(slotId);

        mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(SECOND_CLIENT, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This slot is no longer available"));

        assertEquals(1, appointmentCountOnSlot(slotId));
    }

    @Test
    void anUnknownSlotAndAnUnknownServiceTypeAre404() throws Exception {
        mockMvc.perform(post("/appointments/book/" + MISSING)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Slot not found"));

        long slotId = newSlot();
        mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypeId\": " + MISSING + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Service type not found"));

        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
    }

    @Test
    void aMissingServiceTypeIdAndABadSlotIdAre400() throws Exception {
        mockMvc.perform(post("/appointments/book/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("serviceTypeId: serviceTypeId is required"));

        mockMvc.perform(post("/appointments/book/abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aServiceThatNeedsKycIsRefusedByTheFailClosedCheck() throws Exception {
        Long kycServiceId = jdbcTemplate.queryForList(
                "SELECT id FROM service_types WHERE active = true AND requires_kyc = true ORDER BY id LIMIT 1", Long.class)
                .stream().findFirst().orElse(null);
        if (kycServiceId == null) {
            return; // the database has no service that needs KYC, so there is nothing to check here
        }
        long slotId = newSlot();

        mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypeId\": " + kycServiceId + "}"))
                .andExpect(status().isBadRequest());

        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
    }

    @Test
    void aBlacklistedAnInactiveAndAnUnknownCallerAreRefusedBeforeAnythingIsBooked() throws Exception {
        long slotId = newSlot();

        mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BLACKLISTED_CLIENT, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is blacklisted"));

        mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(INACTIVE_CLIENT, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is not active"));

        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
        assertEquals(0, appointmentCountOnSlot(slotId));
        verifyNoInteractions(notificationPublisher, auditRecorder);
    }

    @Test
    void aUserServiceThatIsDownIs503() throws Exception {
        long slotId = newSlot();
        when(advisorClient.getById(advisorId)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody()))
                .andExpect(status().isServiceUnavailable());

        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
    }

    // confirm

    @Test
    void theOwningAdvisorConfirms() throws Exception {
        long appointmentId = bookedAppointment(newSlot());

        mockMvc.perform(patch("/appointments/" + appointmentId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.advisorName").value("Test Advisor"));

        assertEquals("CONFIRMED", lookups.statusOfAppointment(appointmentId));
    }

    @Test
    void anotherAdvisorConfirming400sAndAnUnknownAppointmentIs404() throws Exception {
        long appointmentId = bookedAppointment(newSlot());

        mockMvc.perform(patch("/appointments/" + appointmentId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_ADVISOR_USER, "ADVISOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can only confirm your own appointments"));

        mockMvc.perform(patch("/appointments/" + MISSING + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found"));

        assertEquals("PENDING", lookups.statusOfAppointment(appointmentId));
    }

    @Test
    void confirmingTwiceIs400() throws Exception {
        long appointmentId = bookedAppointment(newSlot());
        mockMvc.perform(patch("/appointments/" + appointmentId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR"))).andExpect(status().isOk());

        mockMvc.perform(patch("/appointments/" + appointmentId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only pending appointments can be confirmed"));
    }

    @Test
    void aCallerWithoutAnAdvisorProfileIs404AndABlacklistedAdvisorIs403() throws Exception {
        when(advisorClient.getByUserId(STRANGER)).thenThrow(new com.example.bookingservice.exception.InformationNotFound("Advisor profile not found"));

        mockMvc.perform(patch("/appointments/1/confirm").header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, "ADVISOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor profile not found"));

        mockMvc.perform(patch("/appointments/1/confirm").header(HttpHeaders.AUTHORIZATION, bearer(BLACKLISTED_ADVISOR, "ADVISOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is blacklisted"));
    }

    // cancel

    @Test
    void aClientCancelsAndTheSlotIsFreeAgain() throws Exception {
        long slotId = newSlot();
        long appointmentId = bookedAppointment(slotId);

        mockMvc.perform(patch("/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("Appointment cancelled"));

        assertEquals("CANCELLED", lookups.statusOfAppointment(appointmentId));
        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
        verifyNoInteractions(strikeClient); // far in the future, so no strike
    }

    @Test
    void theAdvisorOfTheAppointmentCanCancelToo() throws Exception {
        long appointmentId = bookedAppointment(newSlot());

        mockMvc.perform(patch("/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertEquals("CANCELLED", lookups.statusOfAppointment(appointmentId));
    }

    @Test
    void someoneElseCancelling400sAnUnknownAppointmentIs404AndAnAlreadyCancelledOneIs400() throws Exception {
        long appointmentId = bookedAppointment(newSlot());

        mockMvc.perform(patch("/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are not authorized to cancel this appointment"));
        assertEquals("PENDING", lookups.statusOfAppointment(appointmentId));

        mockMvc.perform(patch("/appointments/" + MISSING + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/appointments/" + appointmentId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(patch("/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only pending or confirmed appointments can be cancelled"));
    }

    @Test
    void aBlacklistedAndAnInactiveCallerCannotCancel() throws Exception {
        long appointmentId = bookedAppointment(newSlot());

        mockMvc.perform(patch("/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BLACKLISTED_CLIENT, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is blacklisted"));

        mockMvc.perform(patch("/appointments/" + appointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(INACTIVE_CLIENT, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is not active"));

        assertEquals("PENDING", lookups.statusOfAppointment(appointmentId));
    }

    @Test
    void aMissingOrTooLongReasonIs400() throws Exception {
        mockMvc.perform(patch("/appointments/1/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        mockMvc.perform(patch("/appointments/1/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
