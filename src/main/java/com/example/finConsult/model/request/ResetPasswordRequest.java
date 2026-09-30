package com.example.finConsult.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
public class ResetPasswordRequest {
    @NotBlank
    @Size(min = 8)
    private String newPassword;
}
