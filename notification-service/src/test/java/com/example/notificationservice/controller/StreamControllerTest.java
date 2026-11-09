package com.example.notificationservice.controller;

import com.example.notificationservice.service.StreamTicketService;
import com.example.notificationservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class StreamControllerTest {

    private static final long USER = 1L;

    @Autowired MockMvc mockMvc;
    @Autowired StreamTicketService streamTicketService;

    // the same secret the app validates with, so the tokens built here are accepted
    @Value("${jwt.secret}") String jwtSecret;

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
        mockMvc.perform(post("/notifications/stream-ticket").header(HttpHeaders.AUTHORIZATION, bearer(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty());
    }

    // the stream

    @Test
    void aValidTicketOpensTheStream() throws Exception {
        String json = mockMvc.perform(post("/notifications/stream-ticket").header(HttpHeaders.AUTHORIZATION, bearer(USER)))
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
        String ticket = streamTicketService.issue(USER);

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
        String ticket = streamTicketService.issue(USER);

        mockMvc.perform(get("/notifications/mine").param("ticket", ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBadTokenIsNotRescuedByATicket() throws Exception {
        String ticket = streamTicketService.issue(USER);
        String forged = "Bearer " + TestTokens.valid(TestTokens.OTHER_SECRET, USER, "CLIENT");

        mockMvc.perform(get("/notifications/stream")
                        .param("ticket", ticket)
                        .header(HttpHeaders.AUTHORIZATION, forged)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }
}
