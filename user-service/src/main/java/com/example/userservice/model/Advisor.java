package com.example.userservice.model;

import com.example.userservice.model.enums.Specialty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "advisors")
public class Advisor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String title; //senior finan specialist, morgage speci etc..

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialty specialty = Specialty.GENERAL;

    @Column(name = "years_of_experience") //used for filter "range 0-2 or 10+"
    private int yearsOfExperience = 0;

    @Column
    private boolean active = true;
}
