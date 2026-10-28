package com.example.finConsult.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserSummaryResponse { // for the admin pickers, just enough to tell people apart
    @Schema(example = "5")
    private Long id;
    @Schema(example = "Maryam Al-Hammadi")
    private String name;
    @Schema(example = "maryam.alhammadi@example.com")
    private String email;
}
