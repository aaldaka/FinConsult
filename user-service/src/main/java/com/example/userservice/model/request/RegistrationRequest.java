package com.example.userservice.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegistrationRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @JsonProperty(required = false) // if its null just a default pfp will be given
    private String profilePicturePath; //can be optional

}
