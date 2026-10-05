package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.Profile;
import com.example.finConsult.model.User;
import com.example.finConsult.model.request.UpdateProfileRequest;
import com.example.finConsult.model.response.ProfileUpdateResponse;
import com.example.finConsult.repository.ProfileRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@AllArgsConstructor
public class ProfileService {
    private final ProfileRepository profileRepository;
    private final FileStorageService fileStorageService;

    private static final long MAX_PICTURE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_PICTURE_TYPES = List.of("image/jpeg", "image/png");
    private static final String DEFAULT_PICTURE_PATH = "/default.png";

    // helper
    private ProfileUpdateResponse toResponse(Profile profile) {
        return new ProfileUpdateResponse(
                profile.getFirstName(),
                profile.getLastName(),
                profile.getPhone(),
                profile.getDateOfBirth(), //default pfp if not provided
                profile.getProfilePicturePath() != null ? profile.getProfilePicturePath() : DEFAULT_PICTURE_PATH
        );
    }

    public ProfileUpdateResponse getMyProfile(User user) {
        Profile profile = getProfileOrThrow(user);
        return toResponse(profile);
    }

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
        //audit logged
        return toResponse(profile);
    }

    @Transactional
    public ProfileUpdateResponse updateProfilePicture(User user, MultipartFile file) {
        Profile profile = getProfileOrThrow(user);

        if (file == null || file.isEmpty()) {
            throw new InvalidRequest("No file was uploaded");
        }

        if (file.getSize() > MAX_PICTURE_SIZE_BYTES){
            throw new InvalidRequest("File size cannot exceed 5 Mbs");
        }

        String type = file.getContentType();
        if (type == null || !ALLOWED_PICTURE_TYPES.contains(type)) {
            throw new InvalidRequest("File type/extension is not allowed");
        }

        String path = fileStorageService.store(file, "profile_pictures");
        profile.setProfilePicturePath(path);
        profileRepository.save(profile);
        return toResponse(profile);
    }

    private Profile getProfileOrThrow(User user) {
        return profileRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Profile not found"));
    }
}