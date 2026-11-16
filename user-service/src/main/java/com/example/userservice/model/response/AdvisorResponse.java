package com.example.userservice.model.response;

import com.example.userservice.model.enums.Specialty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdvisorResponse {
    private Long id;
    private String name;
    private String title;
    private Specialty specialty;
    private int yearsOfExperience;
    private boolean active;
    private String profilePicturePath; // null while the advisor still has the default picture
}
