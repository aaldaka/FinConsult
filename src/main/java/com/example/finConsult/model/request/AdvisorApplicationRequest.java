package com.example.finConsult.model.request;

import com.example.finConsult.model.enums.Specialty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
public class AdvisorApplicationRequest {

    @NotBlank
    private String title;

    private Specialty specialty;

    @Min(0)
    private int yearsOfExperience;
}
