package com.example.finConsult.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest { //only logged in users
    @NotBlank
    @Size(min = 8)
    private String newPassword;

    @NotBlank //confirmation its the same preson
    private String currentPassword;
}
