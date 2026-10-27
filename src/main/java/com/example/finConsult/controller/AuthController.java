package com.example.finConsult.controller;

import com.example.finConsult.model.request.*;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.model.response.Response;
import com.example.finConsult.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Public and self-service account endpoints: registration, login, email
 * verification and password management.
 */
@Tag(name = "Authentication", description = "Register, log in and manage passwords")
@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Log in and receive a JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in", content = @Content(schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password, or account is blacklisted", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Account is unverified or deactivated", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<Response> login(@Valid @RequestBody LoginRequest req){
        Response response = authService.login(req);
        return ResponseEntity.ok(response); //ok 200
    }

    @Operation(summary = "Register a new client account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created", content = @Content(schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/register") //returning data
    public ResponseEntity<Response> register(@Valid @RequestBody RegistrationRequest req){
        Response response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response); //201 with metadata
    }

    @Operation(summary = "Request a password reset email")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset email sent if the address is known", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/forget-password") //just a msg
    public ResponseEntity<MessageResponse> forgetPassword(@Valid @RequestBody ForgetPasswordRequest req){
        MessageResponse response = authService.forgetPassword(req);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Reset a forgotten password using the emailed token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error, or the token is invalid/used/expired", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Account is deactivated", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest req){
        MessageResponse response = authService.resetPassword(req);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Change the current user's password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Current password is incorrect, or missing/invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest req){
        String email = userDetails.getUsername();
        MessageResponse response = authService.changePassword(email, req);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Verify an email address with the emailed token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified", content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "401", description = "The verification link has already been used or has expired", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invalid verification token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@Parameter(description = "Token emailed to the user when they registered") @RequestParam String token){
        VerifyEmailRequest req = new VerifyEmailRequest();
        req.setToken(token);
        MessageResponse response = authService.verifyEmail(req);
        return ResponseEntity.ok(response.getMsg());
    }
}
