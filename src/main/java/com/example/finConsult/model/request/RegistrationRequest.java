package com.example.finConsult.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegistrationRequest {

    @Schema(description = "Email to register with", example = "client@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "Password, minimum 8 characters", example = "Str0ngP@ss!")
    @NotBlank
    @Size(min = 8)
    private String password;

    @Schema(description = "First name", example = "Jane")
    @NotBlank
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    @NotBlank
    private String lastName;

    @Schema(description = "Optional profile picture path; a default picture is used when omitted", nullable = true)
    @JsonProperty(required = false) // if its null just a default pfp will be given
    private String profilePicturePath; //can be optional

}
