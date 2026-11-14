package com.example.documentservice.model.request;

import com.example.documentservice.model.enums.Specialty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdvisorApplicationRequest {

    @NotBlank
    private String title;

    private Specialty specialty;

    @Min(0)
    private int yearsOfExperience;
}
