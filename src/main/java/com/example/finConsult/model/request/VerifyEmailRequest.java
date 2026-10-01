package com.example.finConsult.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
public class VerifyEmailRequest {
    @NotBlank
    private String token;
}