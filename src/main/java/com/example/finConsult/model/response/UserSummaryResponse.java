package com.example.finConsult.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserSummaryResponse { // for the admin pickers, just enough to tell people apart
    private Long id;
    private String name;
    private String email;
}
