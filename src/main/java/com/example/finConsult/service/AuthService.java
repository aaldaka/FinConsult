package com.example.finConsult.service;


import com.example.finConsult.exception.EmailExists;
import com.example.finConsult.exception.InformationIncorrect;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.model.Profile;
import com.example.finConsult.model.User;
import com.example.finConsult.model.VerificationToken;
import com.example.finConsult.model.request.LoginRequest;
import com.example.finConsult.model.request.RegistrationRequest;
import com.example.finConsult.model.response.Response;
import com.example.finConsult.repository.ProfileRepository;
import com.example.finConsult.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import static com.example.finConsult.model.enums.UserRole.*;
import static com.example.finConsult.model.enums.UserStatus.*;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, ProfileRepository profileRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
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

            //email verification token (TODO)

        return new Response(currentUser.getId(), currentUser.getEmail(), userProfile.getFirstName(), userProfile.getLastName(), currentUser.getRole());
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

        //jwt token instead of manual response body.
        return new Response(user.getId(), user.getEmail(), userProfile.getFirstName(), userProfile.getLastName(), user.getRole());

    }
}