package com.example.finConsult.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
public class ResetPasswordRequest {

    @Schema(description = "Token emailed to the user when they requested a password reset", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotBlank
    private String token;

    @Schema(description = "New password, minimum 8 characters", example = "Str0ngP@ss!")
    @NotBlank
    @Size(min = 8)
    private String newPassword;
}
