package com.example.finConsult.model.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReasonRequest {
    @Size(max = 500)
    private String reason;
}