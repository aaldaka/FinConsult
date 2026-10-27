package com.example.finConsult.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@AllArgsConstructor
@Getter
@Setter
public class ProfileUpdateResponse {
    private String firstName;
    private String lastName;
    private String phone;
    private LocalDate dateOfBirth;
    private String profilePicturePath;
}