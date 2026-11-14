package com.example.documentservice.controller;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.client.UserSummary;
import com.example.documentservice.exception.ServiceUnavailable;
import com.example.documentservice.integration.AuditRecorder;
import com.example.documentservice.integration.NotificationPublisher;
import com.example.documentservice.model.enums.AuditAction;
import com.example.documentservice.model.enums.NotificationType;
import com.example.documentservice.repository.KycDocumentRepository;
import com.example.documentservice.service.DocumentProcessor;
import com.example.documentservice.support.TestPeople;
import com.example.documentservice.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). The users are made by TestPeople and deleted afterwards with their documents,
// files go into a temp dir, and the user service, the notification and audit stubs and the background check are mocks.
// Every mock records whether a database transaction was open when it was called, so a test can prove none was
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class KycControllerTest {

    private static final Path UPLOADS = tempDir();

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("kyc-controller-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void deleteTheUploads() throws IOException {
        FileSystemUtils.deleteRecursively(UPLOADS);
    }

    @Autowired MockMvc mockMvc;
    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean UserClient userClient;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean DocumentProcessor documentProcessor;

    @Value("${jwt.secret}") String jwtSecret;

    private TestPeople people;
    private long client;
    private long otherClient;
    private long admin;
    private final List<String> callsInsideATransaction = new CopyOnWriteArrayList<>();
    private int outsideCalls;

    @BeforeEach
    void setUp() {
        people = new TestPeople(jdbcTemplate);
        client = people.newClient();
        otherClient = people.newClient();
        admin = people.newAdmin();

        when(userClient.lookup(any())).thenAnswer(invocation -> {
            noteTransaction("userClient.lookup");
            Map<Long, UserSummary> users = new HashMap<>();
            for (Long id : invocation.<Collection<Long>>getArgument(0)) {
                users.put(id, new UserSummary("user" + id + "@test.com", "Person " + id));
            }
            return users;
        });
        doAnswer(invocation -> noteTransaction("notificationPublisher.publish"))
                .when(notificationPublisher).publish(anyLong(), any(), any());
        doAnswer(invocation -> noteTransaction("auditRecorder.record"))
                .when(auditRecorder).record(any(), anyLong(), anyLong(), any());
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        people.deleteCreated();
    }

    private Object noteTransaction(String call) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            callsInsideATransaction.add(call);
        } else {
            outsideCalls++;
        }
        return null;
    }

    // helpers

    private String bearer(long userId, String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, role);
    }

    private static MockMultipartFile pdf(String part) {
        return new MockMultipartFile(part, "id.pdf", "application/pdf", ("%PDF-1.4 " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartHttpServletRequestBuilder upload(MockMultipartFile file, String type) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/kyc").file(file).param("documentType", type);
    }

    private long submitted(long userId, String type) throws Exception {
        String body = mockMvc.perform(upload(pdf("file"), type).header(HttpHeaders.AUTHORIZATION, bearer(userId, "CLIENT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // what the background check would have written, so the document no longer counts as "being checked"
    private void markChecked(long documentId) {
        jdbcTemplate.update("UPDATE kyc_documents SET detected_type = 'PDF', sha256 = ? WHERE id = ?", UUID.randomUUID().toString(), documentId);
    }

    private MockMultipartHttpServletRequestBuilder replace(long documentId) {
        return multipart(HttpMethod.PUT, "/kyc/" + documentId + "/file").file(pdf("file"));
    }

    private String statusOf(long documentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM kyc_documents WHERE id = ?", String.class, documentId);
    }

    // 401 and 403 by role

    @Test
    void withoutATokenEveryEndpointIs401() throws Exception {
        List<AbstractMockHttpServletRequestBuilder<?>> requests = List.of(
                upload(pdf("file"), "PASSPORT"),
                multipart("/kyc/batch").file(pdf("files")).param("documentTypes", "PASSPORT"),
                replace(1),
                get("/kyc/me"),
                get("/kyc"),
                patch("/kyc/1/approve"),
                patch("/kyc/1/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"),
                get("/kyc/1/download"));
        for (AbstractMockHttpServletRequestBuilder<?> request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theClientEndpointsRefuseAnAdminOrAnAdvisor() throws Exception {
        for (String role : new String[]{"ADMIN", "ADVISOR"}) {
            String token = bearer(admin, role);
            for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
                    upload(pdf("file"), "PASSPORT"),
                    multipart("/kyc/batch").file(pdf("files")).param("documentTypes", "PASSPORT"),
                    replace(1),
                    get("/kyc/me"))) {
                mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
            }
        }
        assertTrue(kycDocumentRepository.findByUserId(admin).isEmpty());
    }

    @Test
    void theAdminEndpointsRefuseAClientOrAnAdvisor() throws Exception {
        long documentId = submitted(client, "PASSPORT");
        for (String role : new String[]{"CLIENT", "ADVISOR"}) {
            String token = bearer(client, role);
            for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
                    get("/kyc"),
                    patch("/kyc/" + documentId + "/approve"),
                    patch("/kyc/" + documentId + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"),
                    get("/kyc/" + documentId + "/download"))) {
                mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
            }
        }
        assertEquals("PENDING", statusOf(documentId));
    }

    // submit

    @Test
    void aClientSubmitsADocumentAndGetsTheMainAppsShape() throws Exception {
        String body = mockMvc.perform(upload(pdf("file"), "PASSPORT").header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userEmail").value("user" + client + "@test.com"))
                .andExpect(jsonPath("$.documentType").value("PASSPORT"))
                .andExpect(jsonPath("$.originalFileName").value("id.pdf"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.flagged").value(false))
                .andExpect(jsonPath("$.flagReason").doesNotExist())
                .andExpect(jsonPath("$.submittedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        assertEquals("PENDING", statusOf(id));
        verify(auditRecorder).record(eq(AuditAction.KYC_SUBMITTED), eq(client), eq(id), contains("Person " + client + " submitted a PASSPORT document"));
        verify(documentProcessor).processKycAll(client, List.of(id));
    }

    @Test
    void aSecondPendingDocumentOfTheSameTypeIs400() throws Exception {
        submitted(client, "NATIONAL_ID");

        mockMvc.perform(upload(pdf("file"), "NATIONAL_ID").header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You already have a pending NATIONAL_ID document"));

        assertEquals(1, kycDocumentRepository.findByUserId(client).size());
    }

    @Test
    void aFileOverFiveMegabytesAWrongTypeOrAnEmptyFileIs400() throws Exception {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        mockMvc.perform(upload(new MockMultipartFile("file", "big.pdf", "application/pdf", tooBig), "PASSPORT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File is too large, max size 5 Mbs"));
        mockMvc.perform(upload(new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1, 2, 3}), "PASSPORT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPEG, PNG, DOCX or PDF files are allowed"));
        mockMvc.perform(upload(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]), "PASSPORT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A file is required"));

        assertTrue(kycDocumentRepository.findByUserId(client).isEmpty());
        verify(documentProcessor, never()).processKycAll(any(), any());
    }

    @Test
    void aBatchSavesOneDocumentPerTypeAndRefusesRepeatsOrMissingTypes() throws Exception {
        mockMvc.perform(multipart("/kyc/batch").file(pdf("files")).file(pdf("files"))
                        .param("documentTypes", "NATIONAL_ID", "PROOF_OF_ADDRESS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].documentType").value("NATIONAL_ID"))
                .andExpect(jsonPath("$[1].documentType").value("PROOF_OF_ADDRESS"));
        verify(documentProcessor).processKycAll(eq(client), any());

        mockMvc.perform(multipart("/kyc/batch").file(pdf("files")).file(pdf("files"))
                        .param("documentTypes", "PASSPORT", "PASSPORT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only one file per document type"));
        mockMvc.perform(multipart("/kyc/batch").file(pdf("files")).file(pdf("files"))
                        .param("documentTypes", "PASSPORT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Each file needs a document type"));

        assertEquals(2, kycDocumentRepository.findByUserId(client).size());
        assertTrue(kycDocumentRepository.findByUserId(otherClient).isEmpty());
    }

    // replace

    @Test
    void aDocumentStillBeingCheckedCannotBeReplacedButACheckedOneGoesBackToPending() throws Exception {
        long id = submitted(client, "PASSPORT");

        mockMvc.perform(replace(id).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This document is still being checked, try again in a moment"));

        markChecked(id);
        jdbcTemplate.update("UPDATE kyc_documents SET status = 'REJECTED', rejection_reason = 'blurry' WHERE id = ?", id);
        clearInvocations(documentProcessor, auditRecorder);

        mockMvc.perform(replace(id).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.detectedType").doesNotExist());

        assertEquals("PENDING", statusOf(id));
        verify(auditRecorder).record(eq(AuditAction.KYC_RESUBMITTED), eq(client), eq(id), any());
        verify(documentProcessor).processKycAll(client, List.of(id));
    }

    @Test
    void someoneElsesDocumentAnUnknownOneOrASecondPendingOfTheTypeCannotBeReplaced() throws Exception {
        long id = submitted(client, "PASSPORT");
        markChecked(id);

        mockMvc.perform(replace(id).header(HttpHeaders.AUTHORIZATION, bearer(otherClient, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This is not your document"));
        mockMvc.perform(replace(999_999_999L).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("KYC document not found"));

        jdbcTemplate.update("UPDATE kyc_documents SET status = 'REJECTED' WHERE id = ?", id);
        submitted(client, "PASSPORT"); // a new pending passport
        mockMvc.perform(replace(id).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You already have a pending PASSPORT document, replace that one instead"));
    }

    // the client's list

    @Test
    void aClientSeesOnlyTheirOwnDocumentsWithoutTheFlagReason() throws Exception {
        long mine = submitted(client, "PASSPORT");
        submitted(otherClient, "PASSPORT");
        jdbcTemplate.update("UPDATE kyc_documents SET flag_reason = 'Matches malware' WHERE id = ?", mine);

        mockMvc.perform(get("/kyc/me").header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine))
                .andExpect(jsonPath("$[0].userEmail").value("user" + client + "@test.com"))
                .andExpect(jsonPath("$[0].flagged").value(true))
                .andExpect(jsonPath("$[0].flagReason").doesNotExist());
    }

    @Test
    void theListsAre503WhenTheUserServiceIsDown() throws Exception {
        submitted(client, "PASSPORT");
        doThrow(new ServiceUnavailable("The user service is not available, please try again later")).when(userClient).lookup(any());

        mockMvc.perform(get("/kyc/me").header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The user service is not available, please try again later"));
        mockMvc.perform(get("/kyc").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isServiceUnavailable());
    }

    // the admin's list

    @Test
    void theAdminListFillsInTheEmailsWithOneUserServiceCallForTheWholePage() throws Exception {
        long first = submitted(client, "PASSPORT");
        long second = submitted(client, "NATIONAL_ID");
        long third = submitted(otherClient, "PASSPORT");
        jdbcTemplate.update("UPDATE kyc_documents SET flag_reason = 'Same file was uploaded by another account' WHERE id = ?", third);
        clearInvocations(userClient);

        String body = mockMvc.perform(get("/kyc").param("size", "100").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verify(userClient, times(1)).lookup(any());
        List<String> emails = JsonPath.read(body, "$.content[?(@.id in [" + first + "," + second + "," + third + "])].userEmail");
        assertEquals(3, emails.size());
        assertTrue(emails.contains("user" + client + "@test.com") && emails.contains("user" + otherClient + "@test.com"));
        List<String> reasons = JsonPath.read(body, "$.content[?(@.id == " + third + ")].flagReason");
        assertEquals(List.of("Same file was uploaded by another account"), reasons, "the admin sees why it was flagged");

        String filtered = mockMvc.perform(get("/kyc").param("documentType", "NATIONAL_ID").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(filtered, "$.content[*].documentType");
        assertTrue(types.stream().allMatch("NATIONAL_ID"::equals));
    }

    // approve and reject

    @Test
    void anAdminApprovesEvenWhileTheCheckIsStillRunning() throws Exception {
        long id = submitted(client, "PASSPORT"); // not checked yet: detected_type and flag_reason are still empty

        mockMvc.perform(patch("/kyc/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.userEmail").value("user" + client + "@test.com"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        assertEquals("APPROVED", statusOf(id));
        assertEquals(admin, jdbcTemplate.queryForObject("SELECT reviewed_by FROM kyc_documents WHERE id = ?", Long.class, id));
        verify(notificationPublisher).publish(client, NotificationType.KYC_REVIEWED, "Your PASSPORT document has been approved.");
        verify(auditRecorder).record(eq(AuditAction.KYC_APPROVED), eq(admin), eq(id), eq("Approved PASSPORT for Person " + client));
    }

    @Test
    void onlyAPendingDocumentCanBeReviewedAndAnUnknownOneIs404() throws Exception {
        long id = submitted(client, "PASSPORT");
        mockMvc.perform(patch("/kyc/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/kyc/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only pending documents can be reviewed"));
        mockMvc.perform(patch("/kyc/999999999/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("KYC document not found"));
    }

    @Test
    void aRejectionNeedsAReasonAndSendsItToTheClient() throws Exception {
        long id = submitted(client, "PASSPORT");

        for (String body : new String[]{"{}", "{\"reason\": \"   \"}"}) {
            mockMvc.perform(patch("/kyc/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("A rejection reason is required"));
        }
        mockMvc.perform(patch("/kyc/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/kyc/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"blurry photo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("blurry photo"));

        verify(notificationPublisher).publish(client, NotificationType.KYC_REVIEWED, "Your PASSPORT document was rejected. Reason: blurry photo");
        verify(auditRecorder).record(eq(AuditAction.KYC_REJECTED), eq(admin), eq(id), eq("Rejected PASSPORT for Person " + client));
    }

    @Test
    void aReviewStillGoesThroughWhenTheUserServiceIsDown() throws Exception {
        long id = submitted(client, "PASSPORT");
        doThrow(new ServiceUnavailable("down")).when(userClient).lookup(any());

        mockMvc.perform(patch("/kyc/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.userEmail").doesNotExist());

        assertEquals("APPROVED", statusOf(id));
        verify(auditRecorder).record(eq(AuditAction.KYC_APPROVED), eq(admin), eq(id), eq("Approved PASSPORT for User " + client));
    }

    // download

    @Test
    void anAdminDownloadsTheFileAndAnUnknownOneIs404() throws Exception {
        long id = submitted(client, "PASSPORT");
        String path = jdbcTemplate.queryForObject("SELECT file_path FROM kyc_documents WHERE id = ?", String.class, id);
        byte[] stored = Files.readAllBytes(UPLOADS.resolve(path.substring("/uploads/".length())));

        mockMvc.perform(get("/kyc/" + id + "/download").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"id.pdf\""))
                .andExpect(content().bytes(stored));
        mockMvc.perform(get("/kyc/999999999/download").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isNotFound());
    }

    // transactions

    @Test
    void theUserServiceAndTheStubsAreNeverCalledInsideATransaction() throws Exception {
        long id = submitted(client, "PASSPORT");
        mockMvc.perform(multipart("/kyc/batch").file(pdf("files")).file(pdf("files"))
                .param("documentTypes", "NATIONAL_ID", "INCOME_PROOF")
                .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT"))).andExpect(status().isCreated());
        markChecked(id);
        mockMvc.perform(replace(id).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/kyc/me").header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/kyc").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(patch("/kyc/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))).andExpect(status().isOk());
        long other = submitted(otherClient, "PASSPORT");
        mockMvc.perform(patch("/kyc/" + other + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"blurry\"}")).andExpect(status().isOk());

        assertEquals(List.of(), callsInsideATransaction, "these calls ran while a transaction was open");
        assertTrue(outsideCalls >= 10, "the calls did happen, outside any transaction (" + outsideCalls + ")");
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
}
