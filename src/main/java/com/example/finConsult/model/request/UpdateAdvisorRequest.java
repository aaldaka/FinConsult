package com.example.finConsult.model.request;

import com.example.finConsult.model.enums.Specialty;
import lombok.*;

@Getter
@Setter
public class UpdateAdvisorRequest {
    private String title;
    private Integer yearsOfExperience;
    private Specialty specialty;
}
