package com.example.finConsult.controller;

import com.example.finConsult.model.request.ForgetPasswordRequest;
import com.example.finConsult.model.request.LoginRequest;
import com.example.finConsult.model.request.RegistrationRequest;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.model.response.Response;
import com.example.finConsult.service.AuthService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Response> login(@Valid @RequestBody LoginRequest req){
        System.out.println("CONTROLLER: Calling login");
        Response response = authService.login(req);
        return ResponseEntity.ok(response); //ok 200
    }

    @PostMapping("/register")
    public ResponseEntity<Response> register(@Valid @RequestBody RegistrationRequest req){
        System.out.println("CONTROLLER: Calling register");
        Response response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response); //201 with metadata
    }

    @PostMapping("/forget-password")
    public ResponseEntity<MessageResponse> forgetPassword(@Valid @RequestBody ForgetPasswordRequest req){
        System.out.println("CONTROLLER: Calling forgetPassword");
        MessageResponse response = authService.forgetPassword(req);
        return ResponseEntity.ok(response);
    }


}
