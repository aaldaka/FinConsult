package com.example.userservice.controller;

import com.example.userservice.model.request.ChangePasswordRequest;
import com.example.userservice.model.request.ForgetPasswordRequest;
import com.example.userservice.model.request.LoginRequest;
import com.example.userservice.model.request.RegistrationRequest;
import com.example.userservice.model.request.ResetPasswordRequest;
import com.example.userservice.model.request.VerifyEmailRequest;
import com.example.userservice.model.response.MessageResponse;
import com.example.userservice.model.response.Response;
import com.example.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public and self-service account endpoints: registration, login, email
 * verification and password management.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Log in and receive a JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Wrong email or password, or the account is blacklisted"),
            @ApiResponse(responseCode = "403", description = "Account is unverified or deactivated")
    })
    @PostMapping("/login")
    public ResponseEntity<Response> login(@Valid @RequestBody LoginRequest req){
        Response response = authService.login(req);
        return ResponseEntity.ok(response); //ok 200
    }

    @Operation(summary = "Register a new client account", description = "A verification email is sent to the address")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created, not verified yet"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Email already in use")
    })
    @PostMapping("/register") //returning data
    public ResponseEntity<Response> register(@Valid @RequestBody RegistrationRequest req){
        Response response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response); //201 with metadata
    }

    @PostMapping("/forget-password") //just a msg
    public ResponseEntity<MessageResponse> forgetPassword(@Valid @RequestBody ForgetPasswordRequest req){
        MessageResponse response = authService.forgetPassword(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest req){
        MessageResponse response = authService.resetPassword(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest req){
        String email = userDetails.getUsername();
        MessageResponse response = authService.changePassword(email, req);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token){
        VerifyEmailRequest req = new VerifyEmailRequest();
        req.setToken(token);
        MessageResponse response = authService.verifyEmail(req);
        return ResponseEntity.ok(response.getMsg());
    }
}
