package com.example.userservice.service;

import com.example.userservice.exception.InformationNotFound;
import com.example.userservice.exception.InvalidRequest;
import com.example.userservice.integration.AuditRecorder;
import com.example.userservice.mapper.ResponseMapper;
import com.example.userservice.model.Profile;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.AuditAction;
import com.example.userservice.model.request.UpdateProfileRequest;
import com.example.userservice.model.response.ProfileUpdateResponse;
import com.example.userservice.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Manages a user's own profile details and profile picture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final long MAX_PICTURE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_PICTURE_TYPES = List.of("image/jpeg", "image/png", "image/jpg");

    private final ProfileRepository profileRepository;
    private final FileStorageService fileStorageService;
    private final AuditRecorder auditRecorder;
    private final ResponseMapper mapper;

    @Transactional(readOnly = true)
    public ProfileUpdateResponse getMyProfile(User user) {
        Profile profile = getProfileOrThrow(user);
        return mapper.toProfile(profile);
    }

    /** Partial update, only non-null fields are applied. */
    @Transactional
    public ProfileUpdateResponse updateProfile(User user, UpdateProfileRequest request) {
        Profile profile = getProfileOrThrow(user);

        if (request.getFirstName()!= null){
            profile.setFirstName(request.getFirstName());
        }
        if(request.getLastName()!= null){
            profile.setLastName(request.getLastName());
        }
        if(request.getDateOfBirth()!= null){
            profile.setDateOfBirth(request.getDateOfBirth());
        }
        if(request.getPhone()!=null){
            profile.setPhone(request.getPhone());
        }

        profileRepository.save(profile);
        auditRecorder.record(user.getId(), AuditAction.PROFILE_UPDATED, "Profile", profile.getId(), "Profile updated");
        log.info("Profile updated for user {}", user.getId());
        return mapper.toProfile(profile);
    }

    /** Replaces the profile picture after validating size and content type. */
    @Transactional
    public ProfileUpdateResponse updateProfilePicture(User user, MultipartFile file) {
        Profile profile = getProfileOrThrow(user);

        if (file == null || file.isEmpty()) {
            throw new InvalidRequest("No file was uploaded");
        }
        if (file.getSize() > MAX_PICTURE_SIZE_BYTES){
            log.warn("Profile picture rejected: file too large for user {}", user.getId());
            throw new InvalidRequest("File size cannot exceed 5 Mbs");
        }
        String type = file.getContentType();
        if (type == null || !ALLOWED_PICTURE_TYPES.contains(type)) {
            log.warn("Profile picture rejected: unsupported type for user {}", user.getId());
            throw new InvalidRequest("File type/extension is not allowed");
        }

        String path = fileStorageService.store(file, "profile_pictures");
        profile.setProfilePicturePath(path);
        profileRepository.save(profile);

        auditRecorder.record(user.getId(), AuditAction.PROFILE_PICTURE_UPDATED, "Profile", profile.getId(), "Profile picture changed");
        log.info("Profile picture updated for user {}", user.getId());
        return mapper.toProfile(profile);
    }

    private Profile getProfileOrThrow(User user) {
        return profileRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Profile not found"));
    }
}
