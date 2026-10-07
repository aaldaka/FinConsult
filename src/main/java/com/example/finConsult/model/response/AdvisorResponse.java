package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.Specialty;
import lombok.*;

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
}