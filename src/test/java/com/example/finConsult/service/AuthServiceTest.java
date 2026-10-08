package com.example.finConsult.service;

import com.example.finConsult.exception.InformationIncorrect;
import com.example.finConsult.model.User;
import com.example.finConsult.model.request.LoginRequest;
import com.example.finConsult.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AuthService authService;

    @Test
    void loginWrongPasswordFails() {
        User user = new User();
        user.setId(1L);
        user.setEmail("a@b.com");
        user.setPassword("hashed");

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setEmail("a@b.com");
        req.setPassword("wrong");

        assertThrows(InformationIncorrect.class, () -> authService.login(req));
    }
}
