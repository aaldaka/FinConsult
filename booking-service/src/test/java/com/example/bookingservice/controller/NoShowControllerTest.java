package com.example.bookingservice.controller;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.StrikeResult;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.UserInfo;
import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.enums.AuditAction;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). The user service, the strike client and the stubs are mocks. The advisor, the
// client and the service type are existing rows (appointments have foreign keys to them), found with read-only queries,
// and only the slots a test creates (and the appointments on them) are deleted. The account check is the real one,
// fed by the mocked user service. A slot is moved to "started 30 minutes ago, ends in 90" with a plain update, because
// a no-show can only be marked 20 minutes after the start and the completion job must not close it first
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NoShowControllerTest {

    // made-up caller ids: refused or rejected before the database is touched, so they need no rows
    private static final long STRANGER = 910_003L;
    private static final long SECOND_CLIENT = 910_004L;
    private static final long BLACKLISTED_ADVISOR = 910_005L;
    private static final long OTHER_ADVISOR_USER = 910_006L;
    private static final long OTHER_ADVISOR_ID = 910_007L;
    private static final long ADMIN = 910_008L;
    private static final long BLACKLISTED_ADMIN = 910_009L;
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
        when(strikeClient.addStrike(clientId)).thenReturn(new StrikeResult(1, false));
        when(strikeClient.removeStrike(clientId)).thenReturn(new StrikeResult(0, false));

        when(advisorClient.getByUserId(OTHER_ADVISOR_USER))
                .thenReturn(new AdvisorInfo(OTHER_ADVISOR_ID, OTHER_ADVISOR_USER, true, "Other Advisor", "Planner", Specialty.GENERAL));

        for (long id : new long[]{clientId, advisorUserId, STRANGER, SECOND_CLIENT, OTHER_ADVISOR_USER, ADMIN}) {
            when(userClient.getById(id)).thenReturn(user(id, "ACTIVE", false));
        }
        when(userClient.getById(BLACKLISTED_ADVISOR)).thenReturn(user(BLACKLISTED_ADVISOR, "ACTIVE", true));
        when(userClient.getById(BLACKLISTED_ADMIN)).thenReturn(user(BLACKLISTED_ADMIN, "ACTIVE", true));
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

    private long bookedAppointment(long slotId) throws Exception {
        String response = mockMvc.perform(post("/appointments/book/" + slotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypeId\": " + serviceId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void confirm(long appointmentId) throws Exception {
        mockMvc.perform(patch("/appointments/" + appointmentId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR"))).andExpect(status().isOk());
    }

    // a confirmed appointment on a slot that started 30 minutes ago
    private long overdueConfirmed() throws Exception {
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        long appointmentId = bookedAppointment(slotId);
        confirm(appointmentId);
        appointments.startedHalfAnHourAgo(slotId);
        return appointmentId;
    }

    private long noShow() throws Exception {
        long appointmentId = overdueConfirmed();
        mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show")
                .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR"))).andExpect(status().isOk());
        return appointmentId;
    }

    private String excuseBody(String reason) {
        return "{\"reason\": \"" + reason + "\"}";
    }

    private List<Long> idsOf(String json) {
        List<Number> ids = JsonPath.read(json, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private String list(String url, long userId, String role) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(userId, role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // 401 and 403 by role

    @Test
    void withoutATokenEveryNewEndpointIs401() throws Exception {
        mockMvc.perform(get("/appointments/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/appointments/no-shows")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/appointments/1/no-show")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/appointments/1/excuse").contentType(MediaType.APPLICATION_JSON).content(excuseBody("x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnAdvisorCanMarkANoShow() throws Exception {
        long appointmentId = overdueConfirmed();

        for (String role : new String[]{"CLIENT", "ADMIN"}) {
            mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show").header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
        }

        assertEquals("CONFIRMED", lookups.statusOfAppointment(appointmentId));
        verifyNoInteractions(strikeClient);
    }

    @Test
    void onlyAnAdminCanExcuseANoShow() throws Exception {
        long appointmentId = noShow();

        for (String role : new String[]{"CLIENT", "ADVISOR"}) {
            mockMvc.perform(patch("/appointments/" + appointmentId + "/excuse")
                            .header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(excuseBody("mistake")))
                    .andExpect(status().isForbidden());
        }

        assertFalse(appointments.isExcused(appointmentId));
        verify(strikeClient, never()).removeStrike(clientId);
    }

    @Test
    void onlyAnAdminCanListTheNoShows() throws Exception {
        for (String role : new String[]{"CLIENT", "ADVISOR"}) {
            mockMvc.perform(get("/appointments/no-shows").header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, role)))
                    .andExpect(status().isForbidden());
        }
    }

    // no-show

    @Test
    void theOwningAdvisorMarksANoShowAndGetsTheMonolithsAppointmentShape() throws Exception {
        long appointmentId = overdueConfirmed();

        mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.advisorId").value(advisorId))
                .andExpect(jsonPath("$.advisorName").value("Test Advisor"))
                .andExpect(jsonPath("$.clientName").value("Test Client"))
                .andExpect(jsonPath("$.serviceName").isNotEmpty())
                .andExpect(jsonPath("$.startTime").isNotEmpty())
                .andExpect(jsonPath("$.excused").value(false));

        assertEquals("NO_SHOW", lookups.statusOfAppointment(appointmentId));
        verify(strikeClient).addStrike(clientId);
        verify(auditRecorder).record(eq(AuditAction.APPOINTMENT_NO_SHOW), eq(advisorUserId), eq(appointmentId), anyString());
        verify(notificationPublisher).publish(eq(clientId), eq(NotificationType.APPOINTMENT_NO_SHOW), anyString(), eq(appointmentId));
    }

    @Test
    void aStrikeThatCannotBeAddedStillLeavesTheNoShowSaved() throws Exception {
        long appointmentId = overdueConfirmed();
        when(strikeClient.addStrike(clientId)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        assertEquals("NO_SHOW", lookups.statusOfAppointment(appointmentId));
    }

    @Test
    void anAppointmentThatStartedLessThanTwentyMinutesAgoIs400() throws Exception {
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        long appointmentId = bookedAppointment(slotId);
        confirm(appointmentId);
        appointments.moveSlot(slotId, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(55));

        mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot mark No Show until 20 minutes has passed from the appointment start time"));

        assertEquals("CONFIRMED", lookups.statusOfAppointment(appointmentId));
        verify(strikeClient, never()).addStrike(clientId);
    }

    @Test
    void anAppointmentThatIsNotConfirmedIs400() throws Exception {
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        long appointmentId = bookedAppointment(slotId); // still PENDING
        appointments.startedHalfAnHourAgo(slotId);

        mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only confirmed appointments can be marked as no-show"));

        assertEquals("PENDING", lookups.statusOfAppointment(appointmentId));
    }

    @Test
    void anotherAdvisorMarking400sAndAnUnknownAppointmentIs404() throws Exception {
        long appointmentId = overdueConfirmed();

        mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_ADVISOR_USER, "ADVISOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can only mark your own appointments"));

        mockMvc.perform(patch("/appointments/" + MISSING + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found"));

        assertEquals("CONFIRMED", lookups.statusOfAppointment(appointmentId));
    }

    @Test
    void aCallerWithoutAnAdvisorProfileIs404AndABlacklistedAdvisorIs403() throws Exception {
        when(advisorClient.getByUserId(STRANGER)).thenThrow(new InformationNotFound("Advisor profile not found"));

        mockMvc.perform(patch("/appointments/1/no-show").header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, "ADVISOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor profile not found"));

        mockMvc.perform(patch("/appointments/1/no-show").header(HttpHeaders.AUTHORIZATION, bearer(BLACKLISTED_ADVISOR, "ADVISOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is blacklisted"));
    }

    @Test
    void aUserServiceThatIsDownIs503BeforeAnythingIsChanged() throws Exception {
        long appointmentId = overdueConfirmed();
        when(advisorClient.getByUserId(advisorUserId)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        mockMvc.perform(patch("/appointments/" + appointmentId + "/no-show")
                        .header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                .andExpect(status().isServiceUnavailable());

        assertEquals("CONFIRMED", lookups.statusOfAppointment(appointmentId));
        verify(strikeClient, never()).addStrike(clientId);
    }

    // excuse

    @Test
    void anAdminExcusesANoShowAndTheStrikeIsRemoved() throws Exception {
        long appointmentId = noShow();
        clearInvocations(notificationPublisher); // the no-show step already sent an APPOINTMENT_NO_SHOW to the client

        mockMvc.perform(patch("/appointments/" + appointmentId + "/excuse")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(excuseBody("mistake")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.excused").value(true))
                .andExpect(jsonPath("$.excuseReason").value("mistake"))
                .andExpect(jsonPath("$.clientName").value("Test Client"));

        assertTrue(appointments.isExcused(appointmentId));
        verify(strikeClient).removeStrike(clientId);
        verify(auditRecorder).record(eq(AuditAction.NO_SHOW_EXCUSED), eq(ADMIN), eq(appointmentId), anyString());
        verify(notificationPublisher).publish(eq(clientId), eq(NotificationType.APPOINTMENT_NO_SHOW),
                eq("Your no-show was excused. Reason: mistake"), eq(appointmentId));
    }

    @Test
    void aStrikeThatCannotBeRemovedStillLeavesTheExcuseSaved() throws Exception {
        long appointmentId = noShow();
        when(strikeClient.removeStrike(clientId)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        mockMvc.perform(patch("/appointments/" + appointmentId + "/excuse")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(excuseBody("mistake")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excused").value(true));

        assertTrue(appointments.isExcused(appointmentId));
    }

    @Test
    void aMissingOrBlankReasonIs400AndATooLongOneToo() throws Exception {
        long appointmentId = noShow();

        for (String body : new String[]{"{}", excuseBody(""), excuseBody("   "), excuseBody("x".repeat(256))}) {
            mockMvc.perform(patch("/appointments/" + appointmentId + "/excuse")
                            .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        assertFalse(appointments.isExcused(appointmentId));
        verify(strikeClient, never()).removeStrike(clientId);
    }

    @Test
    void excusingTwiceAndExcusingSomethingThatIsNotANoShowAre400() throws Exception {
        long appointmentId = noShow();
        mockMvc.perform(patch("/appointments/" + appointmentId + "/excuse")
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(excuseBody("mistake"))).andExpect(status().isOk());

        mockMvc.perform(patch("/appointments/" + appointmentId + "/excuse")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(excuseBody("again")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This no-show was already excused"));

        long confirmedId = overdueConfirmed();
        mockMvc.perform(patch("/appointments/" + confirmedId + "/excuse")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(excuseBody("mistake")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Appointment was not marked as a No-Show"));
    }

    @Test
    void excusingAnUnknownAppointmentIs404() throws Exception {
        mockMvc.perform(patch("/appointments/" + MISSING + "/excuse")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(excuseBody("mistake")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found"));
    }

    @Test
    void aBlacklistedAdminIs403() throws Exception {
        mockMvc.perform(patch("/appointments/1/excuse")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BLACKLISTED_ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(excuseBody("mistake")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is blacklisted"));
    }

    // GET /appointments/mine

    @Test
    void aClientSeesTheirOwnAppointmentsNewestFirstByDefault() throws Exception {
        long first = bookedAppointment(data.createSlot(advisorId, SlotStatus.AVAILABLE).getId());
        Thread.sleep(20);
        long second = bookedAppointment(data.createSlot(advisorId, SlotStatus.AVAILABLE).getId());

        List<Long> ids = idsOf(list("/appointments/mine", clientId, "CLIENT"));

        assertTrue(ids.indexOf(second) >= 0 && ids.indexOf(first) >= 0);
        assertTrue(ids.indexOf(second) < ids.indexOf(first), "createdAt descending when no sort is given");
    }

    @Test
    void aClientCanSortAscendingAndFilterByStatus() throws Exception {
        long first = bookedAppointment(data.createSlot(advisorId, SlotStatus.AVAILABLE).getId());
        Thread.sleep(20);
        long second = bookedAppointment(data.createSlot(advisorId, SlotStatus.AVAILABLE).getId());
        confirm(second);

        List<Long> ascending = idsOf(list("/appointments/mine?sort=createdAt,asc", clientId, "CLIENT"));
        assertTrue(ascending.indexOf(first) < ascending.indexOf(second));

        List<Long> confirmed = idsOf(list("/appointments/mine?status=CONFIRMED", clientId, "CLIENT"));
        assertTrue(confirmed.contains(second));
        assertFalse(confirmed.contains(first));
    }

    @Test
    void aClientListsOnlyTheirOwnAppointmentsInTheMonolithsShape() throws Exception {
        long appointmentId = bookedAppointment(data.createSlot(advisorId, SlotStatus.AVAILABLE).getId());

        assertFalse(idsOf(list("/appointments/mine", SECOND_CLIENT, "CLIENT")).contains(appointmentId));

        String body = list("/appointments/mine?status=PENDING", clientId, "CLIENT");
        assertTrue(idsOf(body).contains(appointmentId));
        List<Map<String, Object>> rows = JsonPath.read(body, "$[?(@.id==" + appointmentId + ")]");
        assertEquals("Test Advisor", rows.get(0).get("advisorName"));
        assertEquals("GENERAL", rows.get(0).get("advisorSpecialty"));
        assertEquals("Test Client", rows.get(0).get("clientName"));
        assertEquals("PENDING", rows.get(0).get("status"));
    }

    @Test
    void anAdvisorSeesTheirOwnAppointmentsAndTheOtherAdvisorSeesNone() throws Exception {
        long appointmentId = bookedAppointment(data.createSlot(advisorId, SlotStatus.AVAILABLE).getId());

        assertTrue(idsOf(list("/appointments/mine", advisorUserId, "ADVISOR")).contains(appointmentId));
        assertTrue(idsOf(list("/appointments/mine?status=PENDING", advisorUserId, "ADVISOR")).contains(appointmentId));
        assertFalse(idsOf(list("/appointments/mine?status=CONFIRMED", advisorUserId, "ADVISOR")).contains(appointmentId));
        assertFalse(idsOf(list("/appointments/mine", OTHER_ADVISOR_USER, "ADVISOR")).contains(appointmentId));
    }

    @Test
    void anAdvisorWithoutAnAdvisorProfileIs404() throws Exception {
        when(advisorClient.getByUserId(STRANGER)).thenThrow(new InformationNotFound("Advisor profile not found"));

        mockMvc.perform(get("/appointments/mine").header(HttpHeaders.AUTHORIZATION, bearer(STRANGER, "ADVISOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor profile not found"));
    }

    @Test
    void anUnsupportedSortFieldAndAnUnknownStatusAre400() throws Exception {
        mockMvc.perform(get("/appointments/mine?sort=notes").header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Cannot sort by notes. Allowed: ")));

        mockMvc.perform(get("/appointments/mine?status=NOPE").header(HttpHeaders.AUTHORIZATION, bearer(clientId, "CLIENT")))
                .andExpect(status().isBadRequest());
    }

    // GET /appointments/no-shows

    @Test
    void theNoShowListIsLatestFirstAndKeepsExcusedOnes() throws Exception {
        long earlierSlot = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        long earlier = bookedAppointment(earlierSlot);
        confirm(earlier);
        appointments.moveSlot(earlierSlot, LocalDateTime.now().minusMinutes(60), LocalDateTime.now().plusMinutes(60));

        long laterSlot = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        long later = bookedAppointment(laterSlot);
        confirm(later);
        appointments.moveSlot(laterSlot, LocalDateTime.now().minusMinutes(40), LocalDateTime.now().plusMinutes(80));

        for (long id : new long[]{earlier, later}) {
            mockMvc.perform(patch("/appointments/" + id + "/no-show").header(HttpHeaders.AUTHORIZATION, bearer(advisorUserId, "ADVISOR")))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(patch("/appointments/" + earlier + "/excuse")
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(excuseBody("mistake"))).andExpect(status().isOk());

        String body = list("/appointments/no-shows", ADMIN, "ADMIN");

        List<Long> ids = idsOf(body);
        assertTrue(ids.contains(earlier) && ids.contains(later), "the excused no-show is still listed");
        assertTrue(ids.indexOf(later) < ids.indexOf(earlier), "latest start first");
        List<String> statuses = JsonPath.read(body, "$[*].status");
        assertTrue(statuses.stream().allMatch("NO_SHOW"::equals));
    }

    @Test
    void anAppointmentThatIsNotANoShowIsNotInTheNoShowList() throws Exception {
        long pending = bookedAppointment(data.createSlot(advisorId, SlotStatus.AVAILABLE).getId());

        assertFalse(idsOf(list("/appointments/no-shows", ADMIN, "ADMIN")).contains(pending));
    }
}
