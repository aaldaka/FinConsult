package com.example.notificationservice.controller;

import com.example.notificationservice.client.UserClient;
import com.example.notificationservice.client.UserSummary;
import com.example.notificationservice.exception.ServiceUnavailable;
import com.example.notificationservice.support.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). The rows are inserted by the test with timestamps far in the future, so they
// are the newest in the table and come first whatever else is there, and are deleted afterwards with the actor the
// test inserted. The user service is a mock that knows only that actor
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AdminLogControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean UserClient userClient;

    @Value("${jwt.secret}") String jwtSecret;

    private long actorId;
    private final List<Long> rowIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        actorId = jdbcTemplate.queryForObject(
                "INSERT INTO users (email, password, role, user_status, blacklisted, no_show_strikes, created_at, updated_at) "
                        + "VALUES (?, 'not-a-real-hash', 'ADMIN', 'ACTIVE', false, 0, now(), now()) RETURNING id",
                Long.class, "admin-logs-" + UUID.randomUUID() + "@test.com");
        when(userClient.lookup(any())).thenAnswer(invocation ->
                invocation.<java.util.Collection<Long>>getArgument(0).contains(actorId)
                        ? Map.of(actorId, new UserSummary("reem@test.com", "Reem Al-Mannai"))
                        : Map.of());
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        rowIds.forEach(id -> jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
    }

    private long row(String action, Long actor, String details, LocalDateTime timestamp) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO audit_logs (action, entity_type, entity_id, actor_id, details, timestamp) VALUES (?, 'Appointment', 42, ?, ?, ?) RETURNING id",
                Long.class, action, actor, details, timestamp);
        rowIds.add(id);
        return id;
    }

    private String bearer(String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, actorId, role);
    }

    private static LocalDateTime future(int day) {
        return LocalDateTime.of(2099, 1, day, 12, 0);
    }

    // the page

    @Test
    void anAdminGetsTheNewestFirstWithTheMainAppsFields() throws Exception {
        long oldest = row("APPOINTMENT_BOOKED", actorId, "booked", future(1));
        long middle = row("KYC_APPROVED", null, "approved by the system", future(2));
        long newest = row("APPOINTMENT_CANCELLED", actorId, "cancelled", future(3));

        mockMvc.perform(get("/admin/logs").param("size", "3").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(newest))
                .andExpect(jsonPath("$.content[1].id").value(middle))
                .andExpect(jsonPath("$.content[2].id").value(oldest))
                .andExpect(jsonPath("$.content[0].actorEmail").value("reem@test.com"))
                .andExpect(jsonPath("$.content[0].actorName").value("Reem Al-Mannai"))
                .andExpect(jsonPath("$.content[0].action").value("APPOINTMENT_CANCELLED"))
                .andExpect(jsonPath("$.content[0].entityType").value("Appointment"))
                .andExpect(jsonPath("$.content[0].entityId").value(42))
                .andExpect(jsonPath("$.content[0].details").value("cancelled"))
                .andExpect(jsonPath("$.content[0].timestamp").value("2099-01-03T12:00:00"))
                .andExpect(jsonPath("$.content[1].actorEmail").value("SYSTEM"))
                .andExpect(jsonPath("$.content[1].actorName").value("System"))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    void theDefaultPageHasTwentyEntries() throws Exception {
        row("APPOINTMENT_BOOKED", actorId, "booked", future(1));

        mockMvc.perform(get("/admin/logs").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void pagingMovesThroughTheEntriesNewestFirst() throws Exception {
        row("APPOINTMENT_BOOKED", actorId, "booked", future(1));
        long second = row("APPOINTMENT_CONFIRMED", actorId, "confirmed", future(2));
        row("APPOINTMENT_CANCELLED", actorId, "cancelled", future(3));

        mockMvc.perform(get("/admin/logs").param("page", "1").param("size", "1").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void theActionFilterKeepsOnlyThatAction() throws Exception {
        long older = row("CERTIFICATE_REPLACED", actorId, "replaced", future(1));
        row("APPOINTMENT_BOOKED", actorId, "booked", future(2));
        long newer = row("CERTIFICATE_REPLACED", actorId, "replaced again", future(3));

        mockMvc.perform(get("/admin/logs").param("action", "CERTIFICATE_REPLACED").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newer))
                .andExpect(jsonPath("$.content[1].id").value(older));
    }

    @Test
    void anUnknownActionIs400() throws Exception {
        mockMvc.perform(get("/admin/logs").param("action", "NOT_AN_ACTION").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for action"));
    }

    @Test
    void theListIs503WhenTheUserServiceIsDown() throws Exception {
        row("APPOINTMENT_BOOKED", actorId, "booked", future(1));
        doThrow(new ServiceUnavailable("The user service is not available, please try again later")).when(userClient).lookup(any());

        mockMvc.perform(get("/admin/logs").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The user service is not available, please try again later"));
    }

    // who may see it

    @Test
    void aClientOrAnAdvisorIs403AndNoTokenIs401() throws Exception {
        for (String role : new String[]{"CLIENT", "ADVISOR"}) {
            mockMvc.perform(get("/admin/logs").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/admin/logs")).andExpect(status().isUnauthorized());
    }
}
