package com.example.finConsult.controller;

import com.example.finConsult.model.User;
import com.example.finConsult.model.request.UpdateProfileRequest;
import com.example.finConsult.model.response.ProfileUpdateResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
