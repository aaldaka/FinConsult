package com.example.bookingservice.controller;

import com.example.bookingservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which the main app's tests build. It only reads the service types
// that are already there, it creates and deletes nothing
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class ServiceTypeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    // the same secret the app validates with, so the tokens built here are accepted
    @Value("${jwt.secret}") String jwtSecret;

    private String bearer(String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, 1L, role);
    }

    private String listAs(String role) throws Exception {
        return mockMvc.perform(get("/service-types").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void listsExactlyTheActiveServiceTypesInTheDatabase() throws Exception {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, description, duration_minutes, requires_kyc, required_document_type, required_specialty "
                        + "FROM service_types WHERE active = true ORDER BY id");
        assertFalse(rows.isEmpty(), "Need service types in finconsult_test. "
                + "Run the main app's tests first (./mvnw test in the project root).");

        List<Map<String, Object>> returned = JsonPath.read(listAs("CLIENT"), "$");
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> item : returned) {
            byId.put(((Number) item.get("id")).longValue(), item);
        }

        assertEquals(rows.size(), returned.size(), "one entry per active service type, no more");
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = byId.get(((Number) row.get("id")).longValue());
            assertEquals(row.get("name"), item.get("name"));
            assertEquals(row.get("description"), item.get("description"));
            assertEquals(((Number) row.get("duration_minutes")).intValue(), ((Number) item.get("durationMinutes")).intValue());
            assertEquals(row.get("requires_kyc"), item.get("requiresKyc"));
            assertEquals(row.get("required_document_type"), item.get("requiredDocumentType"));
            assertEquals(row.get("required_specialty"), item.get("requiredSpecialty"));
        }
    }

    @Test
    void anInactiveServiceTypeIsNotListed() throws Exception {
        List<Long> inactiveIds = jdbcTemplate.queryForList("SELECT id FROM service_types WHERE active = false", Long.class);

        List<Map<String, Object>> returned = JsonPath.read(listAs("CLIENT"), "$");
        for (Map<String, Object> item : returned) {
            assertFalse(inactiveIds.contains(((Number) item.get("id")).longValue()), "inactive service listed: " + item.get("name"));
        }
    }

    @Test
    void theResponseHasTheSameFieldsAsTheMainApp() throws Exception {
        List<Map<String, Object>> returned = JsonPath.read(listAs("CLIENT"), "$");
        assertFalse(returned.isEmpty(), "Need service types in finconsult_test");

        assertEquals(java.util.Set.of("id", "name", "description", "durationMinutes", "requiresKyc",
                "requiredDocumentType", "requiredSpecialty"), returned.get(0).keySet());
    }

    @Test
    void anyLoggedInRoleCanRead() throws Exception {
        for (String role : new String[]{"CLIENT", "ADVISOR", "ADMIN"}) {
            mockMvc.perform(get("/service-types").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void withoutAValidTokenItIsA401() throws Exception {
        mockMvc.perform(get("/service-types")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/service-types").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/service-types").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestTokens.expired(jwtSecret, 1L, "CLIENT")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/service-types").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestTokens.valid(TestTokens.OTHER_SECRET, 1L, "CLIENT")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/service-types").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestTokens.withoutUserId(jwtSecret, "CLIENT")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theHealthEndpointIsOpenAndOtherPathsNeedALogin() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(post("/service-types")).andExpect(status().isUnauthorized());
    }
}
