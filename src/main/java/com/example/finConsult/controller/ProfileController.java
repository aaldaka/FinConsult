package com.example.finConsult.controller;

import com.example.finConsult.model.User;
import com.example.finConsult.model.request.UpdateProfileRequest;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.ProfileUpdateResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service endpoints for the caller's own profile details and picture.
 */
@Tag(name = "Profile", description = "User profile and profile picture")
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController {
    private final ProfileService profileService;


    @Operation(summary = "Get my profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile found", content = @Content(schema = @Schema(implementation = ProfileUpdateResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<ProfileUpdateResponse> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(profileService.getMyProfile(user));
    }

    @Operation(summary = "Update my profile details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated", content = @Content(schema = @Schema(implementation = ProfileUpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/me")
    public ResponseEntity<ProfileUpdateResponse> updateProfile(@AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(profileService.updateProfile(user, request));
    }

    @Operation(summary = "Upload or replace my profile picture")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile picture updated", content = @Content(schema = @Schema(implementation = ProfileUpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing, oversized or unsupported file", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/me/picture")
    public ResponseEntity<ProfileUpdateResponse> updatePicture(@AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("file") MultipartFile file) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(profileService.updateProfilePicture(user, file));
    }
}
