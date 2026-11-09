package com.example.userservice.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StrikeResponse {
    private int noShowStrikes;
    private boolean blacklisted;
}
