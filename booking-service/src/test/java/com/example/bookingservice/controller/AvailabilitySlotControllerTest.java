package com.example.bookingservice.controller;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which the main app's tests build. The user service is a mock, never called.
// The advisor id is an existing advisor (the slots have a foreign key to it), and only the slots a test creates are deleted
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AvailabilitySlotControllerTest {

    private static final long CALLER_USER_ID = 4242L; // the user in the token, the user service mock maps it to the advisor

    @Autowired MockMvc mockMvc;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean AdvisorClient advisorClient;
    @MockitoBean UserClient userClient;

    // the same secret the app validates with, so the tokens built here are accepted
    @Value("${jwt.secret}") String jwtSecret;

    private SlotTestData data;
    private long advisorId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        advisorId = data.anAdvisorId();
        when(advisorClient.getByUserId(CALLER_USER_ID)).thenReturn(info(advisorId, true));
        when(advisorClient.getById(advisorId)).thenReturn(info(advisorId, true));
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    // helpers

    private AdvisorInfo info(long id, boolean active) {
        return new AdvisorInfo(id, CALLER_USER_ID, active, "Test Advisor", "Planner", Specialty.GENERAL);
    }

    private String bearer(String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, CALLER_USER_ID, role);
    }

    private String body(LocalDateTime start, LocalDateTime end) {
        return "{\"startingTime\": \"" + start + "\", \"finishingTime\": \"" + end + "\"}";
    }

    private long slotCountOfAdvisor() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM availability_slots WHERE advisor_id = ?", Long.class, advisorId);
    }

    // POST /slots

    @Test
    void anAdvisorCreatesASlotForTheAdvisorTheUserServiceNames() throws Exception {
        LocalDateTime start = data.freeStart(advisorId);

        String response = mockMvc.perform(post("/slots")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(start, start.plusHours(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.advisorId").value((int) advisorId))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.startTime").exists())
                .andExpect(jsonPath("$.endTime").exists())
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(response, "$.id");
        data.track(id.longValue());
        AvailabilitySlot saved = slotRepository.findById(id.longValue()).orElseThrow();
        assertEquals(advisorId, saved.getAdvisorId());
        assertEquals(SlotStatus.AVAILABLE, saved.getStatus());
        verify(advisorClient).getByUserId(CALLER_USER_ID); // the advisor came from the user in the token
    }

    @Test
    void createNeedsALoginAndTheAdvisorRole() throws Exception {
        LocalDateTime start = data.freeStart(advisorId);
        long before = slotCountOfAdvisor();

        mockMvc.perform(post("/slots").contentType(MediaType.APPLICATION_JSON).content(body(start, start.plusHours(1))))
                .andExpect(status().isUnauthorized());
        for (String role : new String[]{"CLIENT", "ADMIN"}) {
            mockMvc.perform(post("/slots")
                            .header(HttpHeaders.AUTHORIZATION, bearer(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(start, start.plusHours(1))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
        }

        assertEquals(before, slotCountOfAdvisor());
        verifyNoInteractions(advisorClient); // refused before the user service is asked anything
    }

    @Test
    void createRejectsBadInput() throws Exception {
        LocalDateTime start = data.freeStart(advisorId);

        mockMvc.perform(post("/slots").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("startingTime: ")));

        mockMvc.perform(post("/slots").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingTime\": \"" + start + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("finishingTime: finishingTime is required"));

        mockMvc.perform(post("/slots").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(start, start.minusHours(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Slot end time must be after the start time"));

        mockMvc.perform(post("/slots").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request body"));
    }

    @Test
    void createRefusesAnOverlappingSlot() throws Exception {
        AvailabilitySlot existing = data.createSlot(advisorId, SlotStatus.AVAILABLE);

        mockMvc.perform(post("/slots").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(existing.getStartTime().plusMinutes(30), existing.getEndTime().plusMinutes(30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Proposed slot overlap with existing slots"));
    }

    @Test
    void createRefusesAnInactiveAdvisor() throws Exception {
        when(advisorClient.getById(advisorId)).thenReturn(info(advisorId, false));
        LocalDateTime start = data.freeStart(advisorId);
        long before = slotCountOfAdvisor();

        mockMvc.perform(post("/slots").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(start, start.plusHours(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This advisor is no longer available"));

        assertEquals(before, slotCountOfAdvisor());
    }

    @Test
    void createForAUserWhoIsNotAnAdvisorInTheUserServiceIs404() throws Exception {
        when(advisorClient.getByUserId(CALLER_USER_ID)).thenThrow(new InformationNotFound("Advisor profile not found"));
        LocalDateTime start = data.freeStart(advisorId);

        mockMvc.perform(post("/slots").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(start, start.plusHours(1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor profile not found"));
    }

    @Test
    void whenTheUserServiceIsDownCreateIsA503AndNothingIsSaved() throws Exception {
        when(advisorClient.getByUserId(CALLER_USER_ID))
                .thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));
        LocalDateTime start = data.freeStart(advisorId);
        long before = slotCountOfAdvisor();

        mockMvc.perform(post("/slots").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(start, start.plusHours(1))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("The user service is not available, please try again later"));

        assertEquals(before, slotCountOfAdvisor());
    }

    // DELETE /slots/{id}

    @Test
    void anAdvisorDeletesTheirOwnAvailableSlot() throws Exception {
        AvailabilitySlot slot = data.createSlot(advisorId, SlotStatus.AVAILABLE);

        mockMvc.perform(delete("/slots/" + slot.getId()).header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("Slot deleted successfully"));

        assertEquals(false, slotRepository.existsById(slot.getId()));
    }

    @Test
    void deleteNeedsALoginAndTheAdvisorRole() throws Exception {
        AvailabilitySlot slot = data.createSlot(advisorId, SlotStatus.AVAILABLE);

        mockMvc.perform(delete("/slots/" + slot.getId())).andExpect(status().isUnauthorized());
        for (String role : new String[]{"CLIENT", "ADMIN"}) {
            mockMvc.perform(delete("/slots/" + slot.getId()).header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isForbidden());
        }

        assertEquals(true, slotRepository.existsById(slot.getId()));
    }

    @Test
    void deleteRefusesAnotherAdvisorsSlotABookedSlotAndAnUnknownSlot() throws Exception {
        AvailabilitySlot own = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        AvailabilitySlot booked = data.createSlot(advisorId, SlotStatus.BOOKED);

        // the caller is a different advisor than the slot's: the user service says so
        when(advisorClient.getByUserId(CALLER_USER_ID)).thenReturn(info(advisorId + 1_000_000, true));
        mockMvc.perform(delete("/slots/" + own.getId()).header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can only delete your own slots"));

        when(advisorClient.getByUserId(CALLER_USER_ID)).thenReturn(info(advisorId, true));
        mockMvc.perform(delete("/slots/" + booked.getId()).header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete a slot that is not available"));

        mockMvc.perform(delete("/slots/" + Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Slot not found"));

        assertEquals(true, slotRepository.existsById(own.getId()));
        assertEquals(true, slotRepository.existsById(booked.getId()));
    }

    // GET /slots/advisor/{advisorId}

    @Test
    void anyLoggedInRoleSeesTheAvailableSlotsOfAnAdvisorButNotTheBookedOnes() throws Exception {
        AvailabilitySlot available = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        AvailabilitySlot booked = data.createSlot(advisorId, SlotStatus.BOOKED);

        for (String role : new String[]{"CLIENT", "ADVISOR", "ADMIN"}) {
            mockMvc.perform(get("/slots/advisor/" + advisorId).header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(hasItem(available.getId().intValue())))
                    .andExpect(jsonPath("$[*].id").value(not(hasItem(booked.getId().intValue()))))
                    .andExpect(jsonPath("$[?(@.id == " + available.getId() + ")].advisorId").value(hasItem((int) advisorId)))
                    .andExpect(jsonPath("$[?(@.id == " + available.getId() + ")].status").value(hasItem("AVAILABLE")));
        }
    }

    @Test
    void listingNeedsALogin() throws Exception {
        mockMvc.perform(get("/slots/advisor/" + advisorId)).andExpect(status().isUnauthorized());
    }

    @Test
    void anInactiveAdvisorHasAnEmptyListAndAnUnknownAdvisorIs404() throws Exception {
        data.createSlot(advisorId, SlotStatus.AVAILABLE);
        when(advisorClient.getById(advisorId)).thenReturn(info(advisorId, false));
        when(advisorClient.getById(Long.MAX_VALUE)).thenThrow(new InformationNotFound("Advisor not found"));

        mockMvc.perform(get("/slots/advisor/" + advisorId).header(HttpHeaders.AUTHORIZATION, bearer("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/slots/advisor/" + Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer("CLIENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor not found"));
    }

    @Test
    void whenTheUserServiceIsDownListingIsA503() throws Exception {
        when(advisorClient.getById(advisorId)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        mockMvc.perform(get("/slots/advisor/" + advisorId).header(HttpHeaders.AUTHORIZATION, bearer("CLIENT")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void anInvalidAdvisorIdInThePathIsA400() throws Exception {
        mockMvc.perform(get("/slots/advisor/abc").header(HttpHeaders.AUTHORIZATION, bearer("CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for advisorId"));
    }

    // GET /slots/mine

    @Test
    void anAdvisorSeesAllTheirSlotsWhateverTheStatus() throws Exception {
        AvailabilitySlot available = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        AvailabilitySlot booked = data.createSlot(advisorId, SlotStatus.BOOKED);

        mockMvc.perform(get("/slots/mine").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(available.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(hasItem(booked.getId().intValue())))
                .andExpect(jsonPath("$[?(@.id == " + booked.getId() + ")].status").value(hasItem("BOOKED")));
    }

    @Test
    void mineNeedsALoginAndTheAdvisorRole() throws Exception {
        mockMvc.perform(get("/slots/mine")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"CLIENT", "ADMIN"}) {
            mockMvc.perform(get("/slots/mine").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void mineForAUserWhoIsNotAnAdvisorIs404AndWhenTheUserServiceIsDownA503() throws Exception {
        when(advisorClient.getByUserId(CALLER_USER_ID)).thenThrow(new InformationNotFound("Advisor profile not found"));
        mockMvc.perform(get("/slots/mine").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR")))
                .andExpect(status().isNotFound());

        // the mock already throws for this id, so when(...) would call it and throw here: doThrow does not call it
        doThrow(new ServiceUnavailable("The user service is not available, please try again later"))
                .when(advisorClient).getByUserId(CALLER_USER_ID);
        mockMvc.perform(get("/slots/mine").header(HttpHeaders.AUTHORIZATION, bearer("ADVISOR")))
                .andExpect(status().isServiceUnavailable());
    }
}
