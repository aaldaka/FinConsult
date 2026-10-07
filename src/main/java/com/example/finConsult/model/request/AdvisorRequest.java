package com.example.finConsult.model.request;

import com.example.finConsult.model.enums.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
public class AdvisorRequest {

    @Schema(description = "Email for the new advisor account", example = "advisor@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "Password, minimum 8 characters", example = "Str0ngP@ss!")
    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
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
