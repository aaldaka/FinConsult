package com.example.finConsult.model.request;

import com.example.finConsult.model.enums.Specialty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
public class AdvisorRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Min(8)
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    private String title;

    private Specialty specialty;

    private int yearsOfExperience;
}