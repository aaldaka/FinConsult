package com.example.finConsult.model;

import com.example.finConsult.model.enums.UserRole;
import com.example.finConsult.model.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Setter
@Getter
@DynamicUpdate
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) //cannot have 2 emails the same
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) //req is sent with password(user sends it), res is returned without the password
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.CLIENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false)
    private UserStatus userStatus = UserStatus.UNVERIFIED;

    @Column(name = "no_show_strikes") // max 2
    private int noShowStrikes = 0;

    @Column
    private boolean blacklisted = false;
}