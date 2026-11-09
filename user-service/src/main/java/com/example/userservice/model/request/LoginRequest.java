package com.example.userservice.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank //no manual email == null etc
    @Email
    private String email;

    @NotBlank
    private String password;
}
