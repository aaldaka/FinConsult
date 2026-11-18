package com.example.notificationservice.controller;

import com.example.notificationservice.service.StreamTicketService;
import com.example.notificationservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which docker/db/init/00-create-test-db.sh builds. It writes no notifications,
// so there is nothing to clean up, it only needs a real user id
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class StreamControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired StreamTicketService streamTicketService;
    @Autowired JdbcTemplate jdbcTemplate;

    // the same secret the app validates with, so the tokens built here are accepted
    @Value("${jwt.secret}") String jwtSecret;

    private long userA;

    // a user that has no notifications yet
    @BeforeEach
    void findUser() {
        List<Long> users = jdbcTemplate.queryForList(
                "SELECT u.id FROM users u WHERE NOT EXISTS (SELECT 1 FROM notifications n WHERE n.user_id = u.id) ORDER BY u.id LIMIT 1",
                Long.class);
        assertEquals(1, users.size(), "Need a user without notifications in finconsult_test. "
                + "Run the main app's tests first (./mvnw test in the project root), they create the users.");
        userA = users.get(0);
    }

    private String bearer(long userId) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, "CLIENT");
    }

    private ResultActions openStream(String ticket) throws Exception {
        return mockMvc.perform(get("/notifications/stream")
                .param("ticket", ticket)
                .accept(MediaType.TEXT_EVENT_STREAM));
    }

    // the ticket endpoint

    @Test
    void theTicketEndpointNeedsAToken() throws Exception {
        mockMvc.perform(post("/notifications/stream-ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theTicketEndpointReturnsATicket() throws Exception {
        mockMvc.perform(post("/notifications/stream-ticket").header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty());
    }

    // the stream

    @Test
    void aValidTicketOpensTheStream() throws Exception {
        String json = mockMvc.perform(post("/notifications/stream-ticket").header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticket = JsonPath.read(json, "$.ticket");

        openStream(ticket)
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void aReusedTicketIsA401() throws Exception {
        String ticket = streamTicketService.issue(userA);

        openStream(ticket).andExpect(request().asyncStarted());
        openStream(ticket).andExpect(status().isUnauthorized());
    }

    @Test
    void noTicketIsA401() throws Exception {
        mockMvc.perform(get("/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownTicketIsA401() throws Exception {
        openStream("not-a-real-ticket").andExpect(status().isUnauthorized());
    }

    // the ticket is only good for the stream

    @Test
    void aTicketDoesNotOpenAnyOtherEndpoint() throws Exception {
        String ticket = streamTicketService.issue(userA);

        mockMvc.perform(get("/notifications/mine").param("ticket", ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBadTokenIsNotRescuedByATicket() throws Exception {
        String ticket = streamTicketService.issue(userA);
        String forged = "Bearer " + TestTokens.valid(TestTokens.OTHER_SECRET, userA, "CLIENT");

        mockMvc.perform(get("/notifications/stream")
                        .param("ticket", ticket)
                        .header(HttpHeaders.AUTHORIZATION, forged)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }
}
