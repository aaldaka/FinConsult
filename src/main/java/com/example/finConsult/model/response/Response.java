package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Response {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role; // admin client advisor
    private String token; // jwt token, null when registering
}
