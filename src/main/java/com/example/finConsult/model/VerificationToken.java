package com.example.finConsult.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "verification_tokens") // email verifications
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true)
    private String token;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    private boolean used = false; //  if link was pressed or no
}
