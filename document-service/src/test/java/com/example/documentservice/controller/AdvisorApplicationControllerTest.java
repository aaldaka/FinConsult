package com.example.documentservice.controller;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.client.UserInfo;
import com.example.documentservice.exception.NotEligible;
import com.example.documentservice.exception.ServiceUnavailable;
import com.example.documentservice.integration.AuditRecorder;
import com.example.documentservice.integration.NotificationPublisher;
import com.example.documentservice.model.AdvisorApplication;
import com.example.documentservice.model.ApplicationCertificate;
import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.AuditAction;
import com.example.documentservice.model.enums.CertificateStatus;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.NotificationType;
import com.example.documentservice.model.enums.Specialty;
import com.example.documentservice.repository.AdvisorApplicationRepository;
import com.example.documentservice.repository.ApplicationCertificateRepository;
import com.example.documentservice.repository.KycDocumentRepository;
import com.example.documentservice.service.DocumentProcessor;
import com.example.documentservice.service.FileStorageService;
import com.example.documentservice.support.FakeUserService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). The users are made by TestPeople and deleted afterwards with their documents
// and applications, files go into a temp dir, FakeUserService plays the user service, and the notification and audit
// stubs and the background check are mocks. Every call to them records whether a database transaction was open
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AdvisorApplicationControllerTest {

    private static final Path UPLOADS = tempDir();
    private static final String DOWN = "The user service is not available, please try again later";

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("application-controller-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void deleteTheUploads() throws IOException {
        FileSystemUtils.deleteRecursively(UPLOADS);
    }

    @Autowired MockMvc mockMvc;
    @Autowired AdvisorApplicationRepository applicationRepository;
    @Autowired ApplicationCertificateRepository certificateRepository;
    @Autowired KycDocumentRepository kycDocumentRepository;
    @Autowired FileStorageService fileStorageService;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean UserClient userClient;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean DocumentProcessor documentProcessor;

    @Value("${jwt.secret}") String jwtSecret;

    private TestPeople people;
    private FakeUserService users;
    private long client;
    private long otherClient;
    private long admin;
    private final List<String> callsInsideATransaction = new CopyOnWriteArrayList<>();
    private final AtomicInteger outsideCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        people = new TestPeople(jdbcTemplate);
        users = new FakeUserService(userClient, () -> noteTransaction("user service"));
        client = people.newClient();
        otherClient = people.newClient();
        admin = people.newAdmin();
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
            outsideCalls.incrementAndGet();
        }
        return null;
    }

    // helpers

    private String bearer(long userId, String role) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, role);
    }

    private static MockMultipartFile pdf(String part) {
        return new MockMultipartFile(part, "cert.pdf", "application/pdf", ("%PDF-1.4 " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile applicationPart() {
        return new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"title\": \"Retirement Planner\", \"specialty\": \"RETIREMENT_PLANNING\", \"yearsOfExperience\": 4}".getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartHttpServletRequestBuilder submitRequest(MockMultipartFile... certificates) {
        MockMultipartHttpServletRequestBuilder request = multipart("/advisor-applications").file(applicationPart());
        for (MockMultipartFile certificate : certificates) {
            request.file(certificate);
        }
        return request;
    }

    private void giveApprovedIdentity(long userId) {
        KycDocument doc = new KycDocument();
        doc.setUserId(userId);
        doc.setDocumentType(DocumentType.PASSPORT);
        doc.setFilePath("/uploads/none.pdf");
        doc.setOriginalFileName("id.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(10);
        doc.setStatus(KycStatus.APPROVED);
        kycDocumentRepository.save(doc);
    }

    private AdvisorApplication application(long userId, KycStatus status) {
        AdvisorApplication application = new AdvisorApplication();
        application.setUserId(userId);
        application.setTitle("Planner");
        application.setSpecialty(Specialty.INSURANCE);
        application.setYearsOfExperience(6);
        application.setStatus(status);
        return applicationRepository.save(application);
    }

    private ApplicationCertificate certificate(AdvisorApplication application, CertificateStatus status) {
        ApplicationCertificate certificate = new ApplicationCertificate();
        certificate.setApplication(application);
        certificate.setFilePath(fileStorageService.store(pdf("file"), "advisor_applications"));
        certificate.setOriginalFileName("cert.pdf");
        certificate.setContentType("application/pdf");
        certificate.setFileSize(20);
        certificate.setStatus(status);
        return certificateRepository.save(certificate);
    }

    private String statusOf(long applicationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM advisor_applications WHERE id = ?", String.class, applicationId);
    }

    private AbstractMockHttpServletRequestBuilder<?> approve(long applicationId) {
        return patch("/advisor-applications/" + applicationId + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"));
    }

    // 401 and 403 by role

    @Test
    void withoutATokenEveryEndpointIs401() throws Exception {
        for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
                submitRequest(pdf("certificates")),
                get("/advisor-applications/mine"),
                get("/advisor-applications"),
                patch("/advisor-applications/1/approve"),
                patch("/advisor-applications/1/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"),
                multipart(HttpMethod.PUT, "/advisor-applications/certificates/1/file").file(pdf("file")),
                get("/advisor-applications/certificates/1/download"))) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theClientEndpointsRefuseAnAdminOrAnAdvisorAndTheAdminEndpointsAClientOrAnAdvisor() throws Exception {
        for (String role : new String[]{"ADMIN", "ADVISOR"}) {
            for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
                    submitRequest(pdf("certificates")),
                    get("/advisor-applications/mine"),
                    multipart(HttpMethod.PUT, "/advisor-applications/certificates/1/file").file(pdf("file")))) {
                mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(client, role))).andExpect(status().isForbidden());
            }
        }
        for (String role : new String[]{"CLIENT", "ADVISOR"}) {
            for (AbstractMockHttpServletRequestBuilder<?> request : List.<AbstractMockHttpServletRequestBuilder<?>>of(
                    get("/advisor-applications"),
                    patch("/advisor-applications/1/approve"),
                    patch("/advisor-applications/1/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"),
                    get("/advisor-applications/certificates/1/download"))) {
                mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(client, role)))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
            }
        }
        verify(userClient, never()).promote(anyLong(), anyString(), any(), anyInt());
    }

    // submit

    @Test
    void anEligibleClientSubmitsAndTheCertificatesAreCheckedAfterTheCommit() throws Exception {
        giveApprovedIdentity(client);

        String body = mockMvc.perform(submitRequest(pdf("certificates"), pdf("certificates"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicantEmail").value(FakeUserService.email(client)))
                .andExpect(jsonPath("$.applicantName").value(FakeUserService.name(client)))
                .andExpect(jsonPath("$.title").value("Retirement Planner"))
                .andExpect(jsonPath("$.specialty").value("RETIREMENT_PLANNING"))
                .andExpect(jsonPath("$.yearsOfExperience").value(4))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.certificates.length()").value(2))
                .andExpect(jsonPath("$.certificates[0].status").value("PROCESSING"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        List<Number> certificateIds = JsonPath.read(body, "$.certificates[*].id");

        assertEquals("PENDING", statusOf(id));
        verify(documentProcessor).processAll(id, client, certificateIds.stream().map(Number::longValue).toList());
        verify(auditRecorder).record(eq(AuditAction.ADVISOR_APPLICATION_SUBMITTED), eq(client), eq(id),
                eq(FakeUserService.name(client) + " applied to become an advisor"));
    }

    @Test
    void theAccountChecksComeFromTheUserServiceInTheMainAppsOrder() throws Exception {
        giveApprovedIdentity(client);

        users.promoteOutsideTheService(otherClient);
        mockMvc.perform(submitRequest(pdf("certificates")).header(HttpHeaders.AUTHORIZATION, bearer(otherClient, "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only clients can apply to become an advisor"));

        users.deactivate(client);
        users.blacklist(client);
        mockMvc.perform(submitRequest(pdf("certificates")).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Your account must be active to apply"));

        long blacklisted = people.newClient();
        giveApprovedIdentity(blacklisted);
        users.blacklist(blacklisted);
        mockMvc.perform(submitRequest(pdf("certificates")).header(HttpHeaders.AUTHORIZATION, bearer(blacklisted, "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Blacklisted accounts cannot apply to become an advisor"));

        assertTrue(applicationRepository.findByUserId(client).isEmpty());
        assertTrue(applicationRepository.findByUserId(blacklisted).isEmpty());
    }

    @Test
    void theLocalChecksComeNextInTheMainAppsOrder() throws Exception {
        mockMvc.perform(submitRequest(pdf("certificates")).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An approved national ID or passport is required before applying"));

        giveApprovedIdentity(client);
        application(client, KycStatus.PENDING);
        mockMvc.perform(submitRequest(pdf("certificates")).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have an application awaiting review"));

        giveApprovedIdentity(otherClient);
        for (int i = 0; i < 3; i++) {
            application(otherClient, KycStatus.REJECTED);
        }
        mockMvc.perform(submitRequest(pdf("certificates")).header(HttpHeaders.AUTHORIZATION, bearer(otherClient, "CLIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have used all 3 application attempts"));
        assertEquals(3, applicationRepository.countByUserId(otherClient));
    }

    @Test
    void aCertificateIsRequiredAndOnlyPdfJpgOrPngAreAccepted() throws Exception {
        giveApprovedIdentity(client);

        mockMvc.perform(submitRequest(new MockMultipartFile("certificates", "empty.pdf", "application/pdf", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one certificate is required"));
        mockMvc.perform(submitRequest(new MockMultipartFile("certificates", "cv.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1, 2}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Certificates must be PDF, JPG or PNG files"));

        assertTrue(applicationRepository.findByUserId(client).isEmpty());
    }

    @Test
    void aSubmitIs503WhenTheUserServiceIsDownAndNothingIsSaved() throws Exception {
        giveApprovedIdentity(client);
        doThrow(new ServiceUnavailable(DOWN)).when(userClient).getById(anyLong());

        mockMvc.perform(submitRequest(pdf("certificates")).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(DOWN));

        assertTrue(applicationRepository.findByUserId(client).isEmpty());
    }

    // the lists

    @Test
    void aClientSeesTheirApplicationsAndTheAttemptsUsed() throws Exception {
        AdvisorApplication rejected = application(client, KycStatus.REJECTED);
        certificate(rejected, CertificateStatus.FLAGGED);
        application(client, KycStatus.PENDING);
        application(otherClient, KycStatus.PENDING);

        mockMvc.perform(get("/advisor-applications/mine").header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.length()").value(2))
                .andExpect(jsonPath("$.attemptsUsed").value(2))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.applications[?(@.id == " + rejected.getId() + ")].certificates[0].status").value("FLAGGED"))
                .andExpect(jsonPath("$.applications[0].applicantEmail").value(FakeUserService.email(client)));
    }

    @Test
    void theAdminListUsesOneUserServiceCallForThePageAndIs503WhenItIsDown() throws Exception {
        AdvisorApplication first = application(client, KycStatus.PENDING);
        AdvisorApplication second = application(otherClient, KycStatus.PENDING);

        String body = mockMvc.perform(get("/advisor-applications").param("size", "100").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verify(userClient, times(1)).lookup(any());
        List<String> names = JsonPath.read(body, "$.content[?(@.id in [" + first.getId() + "," + second.getId() + "])].applicantName");
        assertTrue(names.containsAll(List.of(FakeUserService.name(client), FakeUserService.name(otherClient))));

        doThrow(new ServiceUnavailable(DOWN)).when(userClient).lookup(any());
        mockMvc.perform(get("/advisor-applications").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/advisor-applications/mine").header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isServiceUnavailable());
    }

    // approve

    @Test
    void approvingPromotesTheUserThenMarksTheApplication() throws Exception {
        AdvisorApplication application = application(client, KycStatus.PENDING);
        certificate(application, CertificateStatus.VALID);

        mockMvc.perform(approve(application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        verify(userClient).promote(client, "Planner", Specialty.INSURANCE, 6);
        assertEquals("APPROVED", statusOf(application.getId()));
        assertEquals(admin, jdbcTemplate.queryForObject("SELECT reviewed_by FROM advisor_applications WHERE id = ?", Long.class, application.getId()));
        verify(notificationPublisher).publish(client, NotificationType.ADVISOR_APPLICATION_REVIEWED,
                "Your advisor application was approved. Log in again to use your advisor account.");
        verify(auditRecorder).record(AuditAction.ADVISOR_APPLICATION_APPROVED, admin, application.getId(),
                "Approved the advisor application of " + FakeUserService.name(client));
    }

    @Test
    void aPromotionRefusedBecauseTheUserIsAlreadyAnAdvisorStillApproves() throws Exception {
        AdvisorApplication application = application(client, KycStatus.PENDING);
        doThrow(new NotEligible("This user is already an advisor"))
                .when(userClient).promote(anyLong(), anyString(), any(), anyInt());
        users.promoteOutsideTheService(client); // has a profile, but the role check still saw a client a moment ago
        doAnswer(invocation -> new UserInfo(client, FakeUserService.email(client),
                "CLIENT", "ACTIVE", false, 0, FakeUserService.name(client))).when(userClient).getById(client);

        mockMvc.perform(approve(application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(userClient).getAdvisorByUser(client);
        assertEquals("APPROVED", statusOf(application.getId()));
    }

    @Test
    void aPromotionRefusedForAnyOtherReasonIs409AndTheApplicationStaysPending() throws Exception {
        AdvisorApplication application = application(client, KycStatus.PENDING);
        doThrow(new NotEligible("Only a client can be promoted to advisor"))
                .when(userClient).promote(anyLong(), anyString(), any(), anyInt());

        mockMvc.perform(approve(application.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a client can be promoted to advisor"));

        assertEquals("PENDING", statusOf(application.getId()));
        verify(notificationPublisher, never()).publish(anyLong(), any(), any());
    }

    @Test
    void aPromotionThatCannotBeMadeIs503AndTheApplicationStaysPending() throws Exception {
        AdvisorApplication application = application(client, KycStatus.PENDING);
        doThrow(new ServiceUnavailable(DOWN)).when(userClient).promote(anyLong(), anyString(), any(), anyInt());

        mockMvc.perform(approve(application.getId()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(DOWN));

        assertEquals("PENDING", statusOf(application.getId()));
        verify(auditRecorder, never()).record(eq(AuditAction.ADVISOR_APPLICATION_APPROVED), anyLong(), anyLong(), any());
    }

    @Test
    void aRetryAfterAnInterruptedApprovalFinishesItWithoutPromotingAgain() throws Exception {
        AdvisorApplication application = application(client, KycStatus.PENDING);
        users.promoteOutsideTheService(client); // an earlier approval promoted the user but never marked the application

        mockMvc.perform(approve(application.getId())).andExpect(status().isOk());

        verify(userClient, never()).promote(anyLong(), anyString(), any(), anyInt());
        assertEquals("APPROVED", statusOf(application.getId()));
    }

    @Test
    void aSecondApproveAfterTheFirstIs400AndPromotesNobodyAgain() throws Exception {
        AdvisorApplication application = application(client, KycStatus.PENDING);
        mockMvc.perform(approve(application.getId())).andExpect(status().isOk());

        mockMvc.perform(approve(application.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only pending applications can be reviewed"));

        assertEquals(1, users.promotions());
    }

    @Test
    void approvingIsRefusedWhileCertificatesAreCheckedOrForAnIneligibleUser() throws Exception {
        AdvisorApplication checking = application(client, KycStatus.PENDING);
        certificate(checking, CertificateStatus.PROCESSING);
        mockMvc.perform(approve(checking.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Certificates are still being checked, try again in a moment"));

        AdvisorApplication blacklisted = application(otherClient, KycStatus.PENDING);
        users.blacklist(otherClient);
        mockMvc.perform(approve(blacklisted.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Blacklisted accounts cannot be promoted to advisor"));

        long inactive = people.newClient();
        AdvisorApplication inactiveOne = application(inactive, KycStatus.PENDING);
        users.deactivate(inactive);
        mockMvc.perform(approve(inactiveOne.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This account is not active"));

        mockMvc.perform(approve(999_999_999L)).andExpect(status().isNotFound());
        verify(userClient, never()).promote(anyLong(), anyString(), any(), anyInt());
    }

    // reject

    @Test
    void aRejectionNeedsAReasonAndSendsItToTheApplicant() throws Exception {
        AdvisorApplication application = application(client, KycStatus.PENDING);

        for (String body : new String[]{"{}", "{\"reason\": \"  \"}"}) {
            mockMvc.perform(patch("/advisor-applications/" + application.getId() + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("A rejection reason is required"));
        }
        mockMvc.perform(patch("/advisor-applications/" + application.getId() + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/advisor-applications/" + application.getId() + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"not enough experience\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("not enough experience"));

        verify(notificationPublisher).publish(client, NotificationType.ADVISOR_APPLICATION_REVIEWED,
                "Your advisor application was rejected. Reason: not enough experience");
        verify(auditRecorder).record(AuditAction.ADVISOR_APPLICATION_REJECTED, admin, application.getId(),
                "Rejected the advisor application of " + FakeUserService.name(client));
        verify(userClient, never()).promote(anyLong(), anyString(), any(), anyInt());
    }

    // replace a certificate

    @Test
    void anApplicantReplacesAFlaggedCertificateAndItIsCheckedAgain() throws Exception {
        AdvisorApplication application = application(client, KycStatus.PENDING);
        ApplicationCertificate flagged = certificate(application, CertificateStatus.FLAGGED);

        mockMvc.perform(multipart(HttpMethod.PUT, "/advisor-applications/certificates/" + flagged.getId() + "/file").file(pdf("file"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificates[0].status").value("PROCESSING"));

        assertEquals(CertificateStatus.PROCESSING, certificateRepository.findById(flagged.getId()).orElseThrow().getStatus());
        verify(documentProcessor).processAll(application.getId(), client, List.of(flagged.getId()));
        verify(auditRecorder).record(eq(AuditAction.CERTIFICATE_REPLACED), eq(client), eq(flagged.getId()), any());
    }

    @Test
    void aCertificateCannotBeReplacedByAnotherUserWhileCheckedOrOnAReviewedApplication() throws Exception {
        AdvisorApplication pending = application(client, KycStatus.PENDING);
        ApplicationCertificate valid = certificate(pending, CertificateStatus.VALID);
        ApplicationCertificate checking = certificate(pending, CertificateStatus.PROCESSING);
        AdvisorApplication rejected = application(client, KycStatus.REJECTED);
        ApplicationCertificate old = certificate(rejected, CertificateStatus.VALID);

        mockMvc.perform(multipart(HttpMethod.PUT, "/advisor-applications/certificates/" + valid.getId() + "/file").file(pdf("file"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherClient, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This is not your certificate"));
        mockMvc.perform(multipart(HttpMethod.PUT, "/advisor-applications/certificates/" + checking.getId() + "/file").file(pdf("file"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This certificate is still being checked, try again in a moment"));
        mockMvc.perform(multipart(HttpMethod.PUT, "/advisor-applications/certificates/" + old.getId() + "/file").file(pdf("file"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only certificates of a pending application can be replaced"));
        mockMvc.perform(multipart(HttpMethod.PUT, "/advisor-applications/certificates/" + valid.getId() + "/file")
                        .file(new MockMultipartFile("file", "x.gif", "image/gif", new byte[]{1}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Certificates must be PDF, JPG or PNG files"));

        verify(documentProcessor, never()).processAll(any(), any(), any());
    }

    // download

    @Test
    void anAdminDownloadsACertificateAndAnUnknownOneIs404() throws Exception {
        ApplicationCertificate certificate = certificate(application(client, KycStatus.PENDING), CertificateStatus.VALID);
        byte[] stored = Files.readAllBytes(UPLOADS.resolve(certificate.getFilePath().substring("/uploads/".length())));

        mockMvc.perform(get("/advisor-applications/certificates/" + certificate.getId() + "/download").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cert.pdf\""))
                .andExpect(content().bytes(stored));
        mockMvc.perform(get("/advisor-applications/certificates/999999999/download").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Certificate not found"));
    }

    // transactions

    @Test
    void theUserServiceAndTheStubsAreNeverCalledInsideATransaction() throws Exception {
        giveApprovedIdentity(client);
        mockMvc.perform(submitRequest(pdf("certificates")).header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT")))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/advisor-applications/mine").header(HttpHeaders.AUTHORIZATION, bearer(client, "CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/advisor-applications").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))).andExpect(status().isOk());

        AdvisorApplication toApprove = application(otherClient, KycStatus.PENDING);
        ApplicationCertificate flagged = certificate(toApprove, CertificateStatus.FLAGGED);
        mockMvc.perform(multipart(HttpMethod.PUT, "/advisor-applications/certificates/" + flagged.getId() + "/file").file(pdf("file"))
                .header(HttpHeaders.AUTHORIZATION, bearer(otherClient, "CLIENT"))).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE application_certificates SET status = 'VALID' WHERE id = ?", flagged.getId());
        mockMvc.perform(approve(toApprove.getId())).andExpect(status().isOk());

        long third = people.newClient();
        AdvisorApplication toReject = application(third, KycStatus.PENDING);
        mockMvc.perform(patch("/advisor-applications/" + toReject.getId() + "/reject").header(HttpHeaders.AUTHORIZATION, bearer(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"no\"}")).andExpect(status().isOk());

        assertEquals(List.of(), callsInsideATransaction, "these calls ran while a transaction was open");
        assertTrue(outsideCalls.get() >= 12, "the calls did happen, outside any transaction (" + outsideCalls.get() + ")");
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
}
