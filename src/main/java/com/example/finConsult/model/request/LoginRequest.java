package com.example.finConsult.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @Schema(description = "Account email", example = "client@example.com")
    @NotBlank //no manual email == null etc
    @Email
    private String email;

    @Schema(description = "Account password", example = "Str0ngP@ss!")
    @NotBlank
    private String password;
}
