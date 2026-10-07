package com.example.finConsult.controller;

import com.example.finConsult.model.request.*;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.model.response.Response;
import com.example.finConsult.service.AuthService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/register") //returning data
    public ResponseEntity<Response> register(@Valid @RequestBody RegistrationRequest req){
        System.out.println("CONTROLLER: Calling register");
        Response response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response); //201 with metadata
    }

    @PostMapping("/forget-password") //just a msg
    public ResponseEntity<MessageResponse> forgetPassword(@Valid @RequestBody ForgetPasswordRequest req){
        System.out.println("CONTROLLER: Calling forgetPassword");
        MessageResponse response = authService.forgetPassword(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest req){
        String email = userDetails.getUsername(); // MyUserDetails.getUsername() returns user.getEmail()
        MessageResponse response = authService.changePassword(email, req);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token){
        System.out.println("CONTROLLER: Calling verifyEmail");
        VerifyEmailRequest req = new VerifyEmailRequest();
        req.setToken(token);
        MessageResponse response = authService.verifyEmail(req);
        return ResponseEntity.ok(response.getMsg());
    }
}
