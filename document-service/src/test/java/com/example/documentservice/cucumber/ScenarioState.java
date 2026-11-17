package com.example.documentservice.cucumber;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

// what one scenario's steps hand to each other: the last response, the caller's token and the document the
// scenario uploaded. A new one is made for every scenario
@Component
@ScenarioScope
@Getter
@Setter
public class ScenarioState {

    private Response response;
    private String token;
    private Long documentId;
}
