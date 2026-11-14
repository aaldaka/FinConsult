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
import com.example.bookingservice.model.enums.DocumentType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.support.BookingLookups;
import com.example.bookingservice.support.SlotTestData;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), like AppointmentControllerTest: existing advisor, client and service type rows
// are found with read-only queries, and only the slots a test creates (and the appointments on them) are deleted. The
// document service is the mocked KycCheck. The KYC tests need a service type that requires KYC; without one in the
// database they are skipped
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class KycBookingControllerTest {

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
    private long clientId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        lookups = new BookingLookups(jdbcTemplate);
        advisorId = data.anAdvisorId();
        clientId = lookups.aClientId();
        AdvisorInfo advisor = new AdvisorInfo(advisorId, lookups.userIdOfAdvisor(advisorId), true, "Test Advisor", "Planner", Specialty.GENERAL);
        when(advisorClient.getById(advisorId)).thenReturn(advisor);
        when(advisorClient.lookup(any())).thenReturn(Map.of(advisorId, advisor));
        when(userClient.names(any())).thenReturn(Map.of(clientId, "Test Client"));
        when(userClient.getById(clientId)).thenReturn(new UserInfo(clientId, "c@test.com", "CLIENT", "ACTIVE", false, 0, "Test Client"));
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    // an active service type that needs KYC, with the document type it asks for (null: any approved document)
    private record KycService(long id, DocumentType requiredType) {}

    private KycService kycService() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, required_document_type FROM service_types WHERE active = true AND requires_kyc = true ORDER BY id LIMIT 1");
        assumeTrue(!rows.isEmpty(), "the database has no active service type that needs KYC");
        Object type = rows.get(0).get("required_document_type");
        return new KycService(((Number) rows.get(0).get("id")).longValue(), type == null ? null : DocumentType.valueOf(type.toString()));
    }

    private ResultActions book(long slotId, long serviceId) throws Exception {
        return mockMvc.perform(post("/appointments/book/" + slotId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(jwtSecret, clientId, "CLIENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceTypeId\": " + serviceId + "}"));
    }

    @Test
    void anApprovedClientBooksAKycServiceAndTheCheckRunsOutsideTheTransaction() throws Exception {
        KycService service = kycService();
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        AtomicBoolean askedInsideATransaction = new AtomicBoolean(false);
        when(kycCheck.isApproved(clientId, service.requiredType())).thenAnswer(invocation -> {
            askedInsideATransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });

        book(slotId, service.id()).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));

        assertFalse(askedInsideATransaction.get(), "the document service was called inside the booking transaction");
        assertEquals("BOOKED", lookups.statusOfSlot(slotId));
    }

    @Test
    void aClientWithoutTheDocumentGetsTheMainAppsMessageAndTheSlotStaysFree() throws Exception {
        KycService service = kycService();
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        when(kycCheck.isApproved(clientId, service.requiredType())).thenReturn(false);

        book(slotId, service.id())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(service.requiredType() == null
                        ? "This service requires an approved KYC document"
                        : "This service requires an approved " + service.requiredType() + " document"));

        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
        assertEquals(0, lookups.activeAppointmentsOnSlot(slotId));
    }

    @Test
    void aDocumentServiceThatIsDownIs503AndNothingIsBooked() throws Exception {
        KycService service = kycService();
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();
        when(kycCheck.isApproved(anyLong(), any()))
                .thenThrow(new ServiceUnavailable("The document service is not available, please try again later"));

        book(slotId, service.id())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The document service is not available, please try again later"));

        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
    }

    @Test
    void aServiceWithoutKycNeverAsksTheDocumentService() throws Exception {
        long slotId = data.createSlot(advisorId, SlotStatus.AVAILABLE).getId();

        book(slotId, lookups.freeServiceTypeId()).andExpect(status().isCreated());

        verifyNoInteractions(kycCheck);
    }
}
