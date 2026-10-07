package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdvisorResponse {
    @Schema(example = "1")
    private Long id;
    @Schema(example = "Jane Doe")
    private String name;
    @Schema(example = "Senior Financial Advisor")
    private String title;
    private Specialty specialty;
    @Schema(example = "5")
    private int yearsOfExperience;
    private boolean active;
}
