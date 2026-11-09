package com.example.userservice.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
    private String newPassword;
}
