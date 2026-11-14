package com.example.documentservice.model.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReasonRequest {
    @Size(max = 255)
    private String reason;
}
