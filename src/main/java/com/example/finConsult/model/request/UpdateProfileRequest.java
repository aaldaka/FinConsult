package com.example.finConsult.model.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateProfileRequest {
    @Size(max = 50)
    private String firstName;
    @Size(max = 50)
    private String lastName;
    @Size(max = 15)
    private String phone;
    private LocalDate dateOfBirth;
}