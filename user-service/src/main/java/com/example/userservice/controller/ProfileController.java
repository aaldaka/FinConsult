package com.example.userservice.controller;

import com.example.userservice.model.User;
import com.example.userservice.model.request.UpdateProfileRequest;
import com.example.userservice.model.response.ProfileUpdateResponse;
import com.example.userservice.security.MyUserDetails;
import com.example.userservice.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service endpoints for the caller's own profile details and picture.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<ProfileUpdateResponse> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(profileService.getMyProfile(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<ProfileUpdateResponse> updateProfile(@AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(profileService.updateProfile(user, request));
    }

    @PostMapping("/me/picture")
    public ResponseEntity<ProfileUpdateResponse> updatePicture(@AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("file") MultipartFile file) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(profileService.updateProfilePicture(user, file));
    }
}
