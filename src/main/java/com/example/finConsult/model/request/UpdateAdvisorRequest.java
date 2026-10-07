package com.example.finConsult.model.request;

import com.example.finConsult.model.enums.Specialty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
public class UpdateAdvisorRequest {
    @Size(max = 50)
    private String title;

    @Min(0)
    @Max(50)
    private Integer yearsOfExperience;
    private Specialty specialty;
}
