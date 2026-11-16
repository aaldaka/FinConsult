package com.example.userservice.mapper;

import com.example.userservice.model.Advisor;
import com.example.userservice.model.Profile;
import com.example.userservice.model.User;
import com.example.userservice.model.response.AdvisorResponse;
import com.example.userservice.model.response.ProfileUpdateResponse;
import com.example.userservice.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Builds the responses of this service. */
@Component
@RequiredArgsConstructor
public class ResponseMapper {

    private final ProfileRepository profileRepository;
    private static final String DEFAULT_PICTURE_PATH = "/default.jpg";

    public ProfileUpdateResponse toProfile(Profile profile) {
        return new ProfileUpdateResponse(
                profile.getFirstName(), profile.getLastName(), profile.getPhone(),
                profile.getDateOfBirth(),
                profile.getProfilePicturePath() != null ? profile.getProfilePicturePath() : DEFAULT_PICTURE_PATH);
    }

    // full name from the profile
    public String nameOf(User user) {
        return profileRepository.findByUser(user)
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Unknown");
    }

    public AdvisorResponse toAdvisor(Advisor a) {
        Optional<Profile> profile = profileRepository.findByUser(a.getUser());
        String name = profile.map(p -> p.getFirstName() + " " + p.getLastName()).orElse("Unknown");
        String picture = profile.map(Profile::getProfilePicturePath)
                .filter(path -> !path.equals(DEFAULT_PICTURE_PATH))
                .orElse(null);
        return new AdvisorResponse(a.getId(), name, a.getTitle(),
                a.getSpecialty(), a.getYearsOfExperience(), a.isActive(), picture);
    }
}
