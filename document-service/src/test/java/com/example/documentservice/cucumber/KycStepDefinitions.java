package com.example.documentservice.cucumber;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.support.FakeUserService;
import com.example.documentservice.support.TestPeople;
import com.example.documentservice.support.TestTokens;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

// the steps of kyc.feature. Every scenario gets its own client and admin from TestPeople, with real tokens signed with
// the app's secret, and they are deleted afterwards with every document they uploaded
public class KycStepDefinitions {

    @LocalServerPort int port;
    @Value("${jwt.secret}") String jwtSecret;

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserClient userClient;
    @Autowired ScenarioState state;

    private TestPeople people;
    private long client;

    @Before
    public void setUp() {
        RestAssured.port = port;
        people = new TestPeople(jdbcTemplate);
        new FakeUserService(userClient, () -> {});
    }

    @After
    public void deleteWhatThisScenarioCreated() {
        people.deleteCreated();
    }

    @AfterAll
    public static void deleteTheUploads() throws IOException {
        FileSystemUtils.deleteRecursively(CucumberSpringConfiguration.UPLOADS);
    }

    // helpers

    private RequestSpecification request() {
        RequestSpecification request = given();
        if (state.getToken() != null) {
            request.header("Authorization", "Bearer " + state.getToken());
        }
        return request;
    }

    private String adminToken() {
        return TestTokens.valid(jwtSecret, people.newAdmin(), "ADMIN");
    }

    // a small pdf, different every time
    private void upload(String documentType) {
        byte[] file = ("%PDF-1.4 " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        state.setResponse(request()
                .multiPart("file", "document.pdf", file, "application/pdf")
                .queryParam("documentType", documentType)
                .when()
                .post("/kyc"));
    }

    // given

    @Given("I am logged in as a client")
    public void iAmLoggedInAsAClient() {
        client = people.newClient();
        state.setToken(TestTokens.valid(jwtSecret, client, "CLIENT"));
    }

    @Given("I am not logged in")
    public void iAmNotLoggedIn() {
        state.setToken(null);
    }

    @Given("I have uploaded a(n) {string} document")
    @Given("I already have a pending {string} document")
    public void iHaveUploadedADocument(String documentType) {
        upload(documentType);
        assertEquals(201, state.getResponse().statusCode(), state.getResponse().asString());
        state.setDocumentId(state.getResponse().jsonPath().getLong("id"));
    }

    // when

    @When("I upload a(n) {string} document")
    @When("I try to upload a(n) {string} document")
    @When("I upload another {string} document")
    public void iUploadADocument(String documentType) {
        upload(documentType);
    }

    @When("the admin approves my document")
    public void theAdminApprovesMyDocument() {
        state.setResponse(given()
                .header("Authorization", "Bearer " + adminToken())
                .when()
                .patch("/kyc/{id}/approve", state.getDocumentId()));
    }

    @When("the admin rejects my document with reason {string}")
    public void theAdminRejectsMyDocument(String reason) {
        state.setResponse(given()
                .header("Authorization", "Bearer " + adminToken())
                .contentType(ContentType.JSON)
                .body(Map.of("reason", reason))
                .when()
                .patch("/kyc/{id}/reject", state.getDocumentId()));
    }

    // then

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertEquals(status, state.getResponse().statusCode(), state.getResponse().asString());
    }

    @Then("the document status is {string}")
    public void theDocumentStatusIs(String status) {
        assertEquals(status, state.getResponse().jsonPath().getString("status"));
    }

    @Then("the rejection reason is {string}")
    public void theRejectionReasonIs(String reason) {
        assertEquals(reason, state.getResponse().jsonPath().getString("rejectionReason"));
    }

    @Then("the error message is {string}")
    public void theErrorMessageIs(String message) {
        assertEquals(message, state.getResponse().jsonPath().getString("message"));
    }
}
