package com.example.documentservice.controller;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.repository.KycDocumentRepository;
import com.example.documentservice.security.InternalKeyFilter;
import com.example.documentservice.support.TestPeople;
import com.example.documentservice.support.TestTokens;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). The client is made by TestPeople and deleted afterwards with its documents
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class InternalKycControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean UserClient userClient;

    @Value("${internal.api-key}") String internalKey;
    @Value("${jwt.secret}") String jwtSecret;

    private TestPeople people;
    private long client;

    @BeforeEach
    void setUp() {
        people = new TestPeople(jdbcTemplate);
        client = people.newClient();
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        people.deleteCreated();
    }

    private void document(DocumentType type, KycStatus status) {
        KycDocument doc = new KycDocument();
        doc.setUserId(client);
        doc.setDocumentType(type);
        doc.setFilePath("/uploads/none.pdf");
        doc.setOriginalFileName("id.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(10);
        doc.setStatus(status);
        kycDocumentRepository.save(doc);
    }

    private ResultActions ask(String documentType) throws Exception {
        var request = get("/internal/kyc/approved").param("userId", String.valueOf(client)).header(InternalKeyFilter.HEADER, internalKey);
        if (documentType != null) {
            request.param("documentType", documentType);
        }
        return mockMvc.perform(request);
    }

    // the key

    @Test
    void withoutTheKeyOrWithAWrongOneIt403s() throws Exception {
        mockMvc.perform(get("/internal/kyc/approved").param("userId", String.valueOf(client)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Missing or invalid internal key"));
        mockMvc.perform(get("/internal/kyc/approved").param("userId", String.valueOf(client)).header(InternalKeyFilter.HEADER, internalKey + "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserTokenWithoutTheKeyIs403EvenForAnAdmin() throws Exception {
        for (String role : new String[]{"ADMIN", "CLIENT", "ADVISOR"}) {
            mockMvc.perform(get("/internal/kyc/approved").param("userId", String.valueOf(client))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(jwtSecret, client, role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void theKeyOpensNothingOutsideInternal() throws Exception {
        mockMvc.perform(get("/kyc").header(InternalKeyFilter.HEADER, internalKey)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/advisor-applications").header(InternalKeyFilter.HEADER, internalKey)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/kyc").header(InternalKeyFilter.HEADER, internalKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(jwtSecret, client, "CLIENT")))
                .andExpect(status().isForbidden());
    }

    // the answer

    @Test
    void noDocumentIsNotApproved() throws Exception {
        ask(null).andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(false));
        ask("PASSPORT").andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(false));
    }

    @Test
    void pendingOrRejectedDocumentsAreNotApproved() throws Exception {
        document(DocumentType.PASSPORT, KycStatus.PENDING);
        document(DocumentType.NATIONAL_ID, KycStatus.REJECTED);

        ask(null).andExpect(jsonPath("$.approved").value(false));
        ask("PASSPORT").andExpect(jsonPath("$.approved").value(false));
        ask("NATIONAL_ID").andExpect(jsonPath("$.approved").value(false));
    }

    @Test
    void anyApprovedDocumentCountsWhenNoTypeIsAsked() throws Exception {
        document(DocumentType.PROOF_OF_ADDRESS, KycStatus.APPROVED);

        ask(null).andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void anApprovedDocumentOfAnotherTypeDoesNotCountButTheRightTypeDoes() throws Exception {
        document(DocumentType.PROOF_OF_ADDRESS, KycStatus.APPROVED);
        ask("INCOME_PROOF").andExpect(jsonPath("$.approved").value(false));

        document(DocumentType.INCOME_PROOF, KycStatus.APPROVED);
        ask("INCOME_PROOF").andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void anInvalidTypeOrAMissingUserIdIs400() throws Exception {
        ask("DRIVING_LICENCE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for documentType"));
        mockMvc.perform(get("/internal/kyc/approved").header(InternalKeyFilter.HEADER, internalKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing value for userId"));
    }
}
