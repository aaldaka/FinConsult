package com.example.finConsult.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @Schema(description = "Token emailed to the user when they requested a password reset", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotBlank
    private String token;

    @Schema(description = "New password: at least 8 characters with an uppercase letter, a lowercase letter, a digit and a special character", example = "Str0ngP@ss!")
    @NotBlank
    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
    private String newPassword;
}
