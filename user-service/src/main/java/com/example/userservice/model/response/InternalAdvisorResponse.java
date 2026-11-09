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
public class InternalAdvisorResponse {
    private boolean exists;
    private boolean active;
    private Long userId;
    private String displayName;
    private String title;
    private Specialty specialty;
}
