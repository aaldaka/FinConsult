package com.example.userservice.model.request;

import com.example.userservice.model.enums.Specialty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

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
