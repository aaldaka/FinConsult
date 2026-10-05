package com.example.finConsult.model.response;

import lombok.*;
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