package com.example.finConsult.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
public class ForgetPasswordRequest {
    @NotBlank
    @Email
    private String email;
}
