package com.example.userservice.model.response;

import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InternalUserResponse {
    private Long id;
    private String email;
    private UserRole role;
    private UserStatus status;
    private boolean blacklisted;
    private int noShowStrikes;
    private String displayName;
}
