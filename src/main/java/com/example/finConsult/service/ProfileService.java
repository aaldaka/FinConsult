package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.Profile;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.model.request.UpdateProfileRequest;
import com.example.finConsult.model.response.ProfileUpdateResponse;
import com.example.finConsult.repository.ProfileRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Manages a user's own profile details and profile picture.
 */
@Slf4j
@Service
@AllArgsConstructor
public class ProfileService {
    private static final long MAX_PICTURE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_PICTURE_TYPES = List.of("image/jpeg", "image/png", "image/jpg");

    private final ProfileRepository profileRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final ResponseMapper mapper;

    public ProfileUpdateResponse getMyProfile(User user) {
        Profile profile = getProfileOrThrow(user);
        return mapper.toProfile(profile);
    }

    /** Partial update — only non-null fields on the request are applied. */
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
        auditLogService.log(user, AuditAction.PROFILE_UPDATED, "Profile", profile.getId(), "Profile updated");
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
        auditLogService.log(user, AuditAction.PROFILE_PICTURE_UPDATED, "Profile", profile.getId(), "Profile picture changed");
        log.info("Profile picture updated for user {}", user.getId());
        return mapper.toProfile(profile);
    }

    private Profile getProfileOrThrow(User user) {
        return profileRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Profile not found"));
    }
}