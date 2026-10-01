package com.example.finConsult.service;


import com.example.finConsult.exception.EmailExists;
import com.example.finConsult.exception.InformationIncorrect;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.model.PasswordResetToken;
import com.example.finConsult.model.Profile;
import com.example.finConsult.model.User;
import com.example.finConsult.model.VerificationToken;
import com.example.finConsult.model.request.*;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.model.response.Response;
import com.example.finConsult.repository.PasswordResetTokenRepository;
import com.example.finConsult.repository.ProfileRepository;
import com.example.finConsult.repository.UserRepository;
import com.example.finConsult.repository.VerificationTokenRepository;
import com.example.finConsult.security.JWTUtils;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.example.finConsult.model.enums.UserRole.*;
import static com.example.finConsult.model.enums.UserStatus.*;

@AllArgsConstructor
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JWTUtils jwtUtils;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;

    @Transactional
    public MessageResponse verifyEmail(VerifyEmailRequest request) { // added link verification
        VerificationToken verificationToken = verificationTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InformationNotFound("Invalid verification token."));

        if (verificationToken.isUsed()) {
            throw new InformationIncorrect("This verification link has been used.");
        }
        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InformationIncorrect("This verification link has expired. Resend another request.");
        }

        User user = verificationToken.getUser();
        user.setUserStatus(ACTIVE);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);

        return new MessageResponse("Email verified successfully.");
    }

    @Transactional //following ACID
    public Response register(RegistrationRequest request) {
            if(userRepository.existsByEmail(request.getEmail())){
                throw new EmailExists("This email is already in use.");
            }

            User currentUser = new User();
            currentUser.setEmail(request.getEmail());
            currentUser.setPassword(passwordEncoder.encode(request.getPassword()));
            currentUser.setUserStatus(UNVERIFIED);
            currentUser.setRole(CLIENT);
            userRepository.save(currentUser);

            Profile userProfile = new Profile();
            userProfile.setUser(currentUser); //newly registered/created user
            userProfile.setFirstName(request.getFirstName());
            userProfile.setLastName(request.getLastName());
            profileRepository.save(userProfile);

        String verifyToken = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(currentUser);
        verificationToken.setToken(verifyToken);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        verificationTokenRepository.save(verificationToken);

        emailService.sendEmail( //to, sub, body || send link when frontend is ready
                currentUser.getEmail(),
                "Verify your FinConsult email",
//                "Please verify your email using this token: " + verifyToken + "\n\nThis token expires in 24 hours."
                "Click to verify your email: http://localhost:8080/auth/verify-email?token=" + verifyToken
                        + "\n\nThis link expires in 24 hours."
        );

// initially when registering null no token
        return new Response(currentUser.getId(), currentUser.getEmail(), userProfile.getFirstName(), userProfile.getLastName(), currentUser.getRole(), null);
    }

    public Response login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InformationIncorrect("Invalid Email or User does not exist."));

        if (user.isBlacklisted()) {
            throw new InformationIncorrect("This account is blocked from logging in");
        }

        String pswd = request.getPassword();
        if (!passwordEncoder.matches(pswd, user.getPassword())){
            throw new InformationIncorrect("Entered password is incorrect.");
        }

        Profile userProfile = profileRepository.findByUser(user)
                .orElseThrow(() -> new InformationIncorrect("User does not exist."));

        String token = jwtUtils.generateToken(user.getEmail());

        return new Response(user.getId(), user.getEmail(), userProfile.getFirstName(), userProfile.getLastName(), user.getRole(), token);
    }

    public MessageResponse forgetPassword(ForgetPasswordRequest pswdReq){
        userRepository.findByEmail(pswdReq.getEmail()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(token);
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            passwordResetTokenRepository.save(resetToken);

            emailService.sendEmail( //send link when frontend is ready
                    user.getEmail(),
                    "Reset your FinConsult password",
                    "Use this token to reset your password: " + token + "\n\nThis token expires in 15 minutes. If you didn't request this, ignore this email."
            );
        });

        return new MessageResponse("A password reset link has been sent to the designated email.");
    }

    // change password method - logged in
    @Transactional
    public MessageResponse changePassword(String email, ChangePasswordRequest changePasswordRequest) {
        User user = userRepository.findByEmail(email).orElseThrow(()-> new InformationNotFound("Email not found"));

        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())){
            throw new InformationIncorrect("Entered password (current) is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword())); //encode it
        userRepository.save(user);
        return new MessageResponse("Password changed successfully.");
    }

    }