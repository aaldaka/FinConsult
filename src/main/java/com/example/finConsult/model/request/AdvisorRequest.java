package com.example.finConsult.model.request;

import com.example.finConsult.model.enums.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdvisorRequest {

    @Schema(description = "Email for the new advisor account", example = "advisor@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "Password: at least 8 characters with an uppercase letter, a lowercase letter, a digit and a special character", example = "Str0ngP@ss!")
    @NotBlank
    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
    private String password;

    @Schema(description = "First name", example = "John")
    @NotBlank
    private String firstName;

    @Schema(description = "Last name", example = "Smith")
    @NotBlank
    private String lastName;

    @Schema(description = "Professional title", example = "Senior Financial Advisor")
    @NotBlank
    private String title;

    @Schema(description = "Advisor's specialty; defaults to GENERAL when omitted", nullable = true)
    private Specialty specialty;

    @Schema(description = "Years of professional experience", example = "5")
    @Min(0)
    private int yearsOfExperience;
}
