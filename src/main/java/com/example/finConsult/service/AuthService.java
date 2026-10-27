package com.example.finConsult.service;


import com.example.finConsult.exception.*;
import com.example.finConsult.model.PasswordResetToken;
import com.example.finConsult.model.Profile;
import com.example.finConsult.model.User;
import com.example.finConsult.model.VerificationToken;
import com.example.finConsult.model.enums.UserStatus;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.example.finConsult.model.enums.UserRole.CLIENT;
import static com.example.finConsult.model.enums.UserStatus.ACTIVE;
import static com.example.finConsult.model.enums.UserStatus.UNVERIFIED;

/**
 * Registration, login, email verification and password management for client
 * accounts.
 */
@Slf4j
@AllArgsConstructor
@Service
public class AuthService {

    /** How long a password reset token stays valid, in minutes; drives both the token's expiry and the emailed text. */
    private static final int RESET_TOKEN_MINUTES = 5;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JWTUtils jwtUtils;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final Environment environment;

    /**
     * Activates an account using the token emailed at registration.
     *
     * @param request carries the verification token
     * @return a confirmation message
     * @throws InformationNotFound   if the token doesn't exist
     * @throws InformationIncorrect  if the token was already used or has expired
     */
    @Transactional
    public MessageResponse verifyEmail(VerifyEmailRequest request) { // added link verification
        VerificationToken verificationToken = verificationTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> {
                    log.warn("Email verification rejected: token not found");
                    return new InformationNotFound("Invalid verification token.");
                });

        if (verificationToken.isUsed()) {
            log.warn("Email verification rejected: token already used for user {}", verificationToken.getUser().getId());
            throw new InformationIncorrect("This verification link has been used.");
        }
        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Email verification rejected: token expired for user {}", verificationToken.getUser().getId());
            throw new InformationIncorrect("This verification link has expired. Resend another request.");
        }

        User user = verificationToken.getUser();
        if (user.getUserStatus() != UNVERIFIED) {
            log.warn("Email verification rejected: user {} is {}", user.getId(), user.getUserStatus());
            throw new InformationIncorrect("This account cannot be verified.");
        }
        user.setUserStatus(ACTIVE);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);

        log.info("Email verified for user {}", user.getId());
        return new MessageResponse("Email verified successfully.");
    }

    /**
     * Registers a new client account, starting it as {@code UNVERIFIED} until the
     * emailed verification link is used.
     *
     * @param request the new account's details
     * @return the created account's public details (no token yet — must verify first)
     * @throws EmailExists if the email is already registered
     */
    @Transactional //following ACID
    public Response register(RegistrationRequest request) {
            if(userRepository.existsByEmail(request.getEmail())){
                log.warn("Registration rejected: email {} already in use", request.getEmail());
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
            userProfile.setProfilePicturePath("/default.jpg"); //added default upon creation
            profileRepository.save(userProfile);

        String verifyToken = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(currentUser);
        verificationToken.setToken(verifyToken);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        verificationTokenRepository.save(verificationToken);

        String frontendUrl = environment.getProperty("app.frontend-url", "http://localhost:5173");

        emailService.sendEmail(
                currentUser.getEmail(),
                "Verify your FinConsult email",
                "Click to verify your email: " + frontendUrl + "/verify-email?token=" + verifyToken
                        + "\n\nThis link expires in 24 hours.",
                "verification"
        );

        log.info("User {} registered", currentUser.getId());
// initially when registering null no token
        return new Response(currentUser.getId(), currentUser.getEmail(), userProfile.getFirstName(), userProfile.getLastName(), currentUser.getRole(), null);
    }

    /**
     * Authenticates a user and issues a JWT. After the password matches, also rejects
     * blacklisted accounts (3+ no-show strikes) and accounts that are unverified or
     * deactivated, so a correct password alone isn't enough to get a token once an
     * account has been banned or disabled.
     *
     * @param request login credentials
     * @return the account's public details plus a JWT
     * @throws InformationIncorrect if the email/password is wrong or the account is blacklisted
     * @throws AccountNotActive     if the account is unverified or deactivated
     */
    public Response login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: unknown email {}", request.getEmail());
                    return new InformationIncorrect("Invalid Email or User does not exist.");
                });

        String pswd = request.getPassword();
        if (!passwordEncoder.matches(pswd, user.getPassword())){
            log.warn("Login failed: incorrect password for user {}", user.getId());
            throw new InformationIncorrect("Entered password is incorrect.");
        }

        if (user.isBlacklisted()) {
            log.warn("Login blocked: user {} is blacklisted", user.getId());
            throw new InformationIncorrect("This account is blocked from logging in");
        }

        if (user.getUserStatus() == UserStatus.UNVERIFIED) {
            log.warn("Login blocked: user {} is unverified", user.getId());
            throw new AccountNotActive("Please verify your email.");
        }

        if (user.getUserStatus() == UserStatus.INACTIVE) {
            log.warn("Login blocked: user {} is deactivated", user.getId());
            throw new AccountNotActive("This account has been deactivated");
        }

        Profile userProfile = profileRepository.findByUser(user)
                .orElseThrow(() -> new InformationIncorrect("User does not exist."));

        String token = jwtUtils.generateToken(user.getEmail());

        log.info("User {} logged in with role {}", user.getId(), user.getRole());
        return new Response(user.getId(), user.getEmail(), userProfile.getFirstName(), userProfile.getLastName(), user.getRole(), token);
    }

    /**
     * Sends a password reset token by email when the address matches an account.
     * Always returns the same message regardless of whether the email exists, to
     * avoid leaking which emails are registered.
     *
     * @param pswdReq the email to send a reset token to
     * @return a generic confirmation message
     */
    public MessageResponse forgetPassword(ForgetPasswordRequest pswdReq){
        log.info("Password reset requested for email {}", pswdReq.getEmail());

        userRepository.findByEmail(pswdReq.getEmail()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(token);
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_MINUTES));
            passwordResetTokenRepository.save(resetToken);

            String frontendUrl = environment.getProperty("app.frontend-url", "http://localhost:5173");

            emailService.sendEmail(
                    user.getEmail(),
                    "Reset your FinConsult password",
                    "Click to reset your password: " + frontendUrl + "/reset-password?token=" + token
                            + "\n\nThis link expires in " + RESET_TOKEN_MINUTES + " minutes. If you didn't request this, ignore this email.",
                    "password-reset"
            );
        });

        return new MessageResponse("A password reset link has been sent to the designated email.");
    }

    /**
     * Resets a user's password using the emailed token. Every failure path (missing,
     * used, or expired token) returns the exact same message, so an attacker probing
     * tokens can't tell which reason applied. On success, every other unused reset
     * token belonging to the user is invalidated too, so an older emailed link can't
     * still be used after a newer one succeeded.
     *
     * @param request the reset token and new password
     * @return a confirmation message
     * @throws InvalidRequest   if the token doesn't exist, was already used, or has expired
     * @throws AccountNotActive if the account is deactivated
     */
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> {
                    log.warn("Password reset rejected: token not found");
                    return new InvalidRequest("Invalid or expired reset token");
                });

        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Password reset rejected: token already used or expired for user {}", resetToken.getUser().getId());
            throw new InvalidRequest("Invalid or expired reset token");
        }

        User user = resetToken.getUser();

        if (user.getUserStatus() == UserStatus.INACTIVE) {
            log.warn("Password reset blocked: user {} is deactivated", user.getId());
            throw new AccountNotActive("This account has been deactivated");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        for (PasswordResetToken other : passwordResetTokenRepository.findByUserAndUsedFalse(user)) {
            other.setUsed(true);
            passwordResetTokenRepository.save(other);
        }

        log.info("Password reset completed for user {}", user.getId());
        return new MessageResponse("Password has been reset. You can now log in.");
    }

    // change password method - logged in
    /**
     * Changes a logged-in user's password after verifying their current one.
     *
     * @param email                 the authenticated user's email
     * @param changePasswordRequest current and new password
     * @return a confirmation message
     * @throws InformationNotFound  if the email doesn't match an account
     * @throws InformationIncorrect if the current password is wrong
     */
    @Transactional
    public MessageResponse changePassword(String email, ChangePasswordRequest changePasswordRequest) {
        User user = userRepository.findByEmail(email).orElseThrow(()-> new InformationNotFound("Email not found"));

        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())){
            log.warn("Password change failed: incorrect current password for user {}", user.getId());
            throw new InformationIncorrect("Entered password (current) is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword())); //encode it
        userRepository.save(user);
        log.info("Password changed for user {}", user.getId());
        return new MessageResponse("Password changed successfully.");
    }

    }