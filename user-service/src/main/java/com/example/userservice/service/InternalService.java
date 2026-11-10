package com.example.userservice.service;

import com.example.userservice.exception.InformationNotFound;
import com.example.userservice.exception.NotEligible;
import com.example.userservice.mapper.ResponseMapper;
import com.example.userservice.model.Advisor;
import com.example.userservice.model.Profile;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import com.example.userservice.model.request.PromoteAdvisorRequest;
import com.example.userservice.model.response.InternalAdminResponse;
import com.example.userservice.model.response.InternalAdvisorResponse;
import com.example.userservice.model.response.InternalUserResponse;
import com.example.userservice.model.response.PromoteAdvisorResponse;
import com.example.userservice.model.response.StrikeResponse;
import com.example.userservice.repository.AdvisorRepository;
import com.example.userservice.repository.ProfileRepository;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** What the other services ask this one for, behind /internal/**. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final AdvisorRepository advisorRepository;
    private final ResponseMapper mapper;

    @Transactional(readOnly = true)
    public InternalUserResponse getUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new InformationNotFound("User not found"));
        return new InternalUserResponse(user.getId(), user.getEmail(), user.getRole(), user.getUserStatus(),
                user.isBlacklisted(), user.getNoShowStrikes(), mapper.nameOf(user));
    }

    /** id to display name; ids that do not exist are left out. Two queries however many ids there are. */
    @Transactional(readOnly = true)
    public Map<Long, String> getNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        return namesOf(userRepository.findAllById(ids.stream().filter(Objects::nonNull).toList()));
    }

    // user id to display name for these users, with one profile query. Same wording as ResponseMapper.nameOf
    private Map<Long, String> namesOf(List<User> users) {
        Map<Long, String> names = new HashMap<>();
        Map<Long, Profile> profiles = profileRepository.findByUserIn(users).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
        for (User user : users) {
            Profile p = profiles.get(user.getId());
            names.put(user.getId(), p == null ? "Unknown" : p.getFirstName() + " " + p.getLastName());
        }
        return names;
    }

    /** Uses the same atomic update as the monolith, so concurrent no-shows are counted correctly. */
    @Transactional
    public StrikeResponse addStrike(Long userId) {
        requireUser(userId);
        int strikes = userRepository.addStrike(userId);
        boolean blacklisted = userRepository.isBlacklisted(userId);
        log.info("No-show strike added to user {}: {} strikes, blacklisted: {}", userId, strikes, blacklisted);
        return new StrikeResponse(strikes, blacklisted);
    }

    @Transactional
    public StrikeResponse removeStrike(Long userId) {
        requireUser(userId);
        int strikes = userRepository.removeStrike(userId);
        boolean blacklisted = userRepository.isBlacklisted(userId);
        log.info("No-show strike removed from user {}: {} strikes, blacklisted: {}", userId, strikes, blacklisted);
        return new StrikeResponse(strikes, blacklisted);
    }

    @Transactional(readOnly = true)
    public List<InternalAdminResponse> getActiveAdmins() {
        return userRepository.findByRoleAndUserStatusOrderByIdAsc(UserRole.ADMIN, UserStatus.ACTIVE).stream()
                .map(u -> new InternalAdminResponse(u.getId(), u.getEmail(), mapper.nameOf(u)))
                .toList();
    }

    @Transactional(readOnly = true)
    public InternalAdvisorResponse getAdvisor(Long id) {
        Advisor advisor = advisorRepository.findById(id).orElseThrow(() -> new InformationNotFound("Advisor not found"));
        return toInternalAdvisor(advisor, mapper.nameOf(advisor.getUser()));
    }

    /** Same info as {@link #getAdvisor}, found through the advisor's user. 404 when that user has no advisor row. */
    @Transactional(readOnly = true)
    public InternalAdvisorResponse getAdvisorByUser(Long userId) {
        Advisor advisor = advisorRepository.findByUserId(userId).orElseThrow(() -> new InformationNotFound("Advisor not found"));
        return toInternalAdvisor(advisor, mapper.nameOf(advisor.getUser()));
    }

    /** advisor id to info; ids that do not exist are left out. Names come from one profile query. */
    @Transactional(readOnly = true)
    public Map<Long, InternalAdvisorResponse> lookupAdvisors(Collection<Long> ids) {
        Map<Long, InternalAdvisorResponse> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<Advisor> advisors = advisorRepository.findAllById(ids.stream().filter(Objects::nonNull).toList());
        Map<Long, String> names = namesOf(advisors.stream().map(Advisor::getUser).toList());
        for (Advisor advisor : advisors) {
            result.put(advisor.getId(), toInternalAdvisor(advisor, names.get(advisor.getUser().getId())));
        }
        return result;
    }

    private InternalAdvisorResponse toInternalAdvisor(Advisor advisor, String displayName) {
        return new InternalAdvisorResponse(advisor.getId(), true, advisor.isActive(), advisor.getUser().getId(),
                displayName, advisor.getTitle(), advisor.getSpecialty());
    }

    /**
     * What the monolith does when an advisor application is approved: the user becomes an ADVISOR and gets an
     * active advisor row, in one transaction. The application itself, notifications and the audit entry belong to the caller.
     */
    @Transactional
    public PromoteAdvisorResponse promote(PromoteAdvisorRequest req) {
        User user = userRepository.findById(req.getUserId()).orElseThrow(() -> new InformationNotFound("User not found"));

        if (user.getRole() == UserRole.ADVISOR || advisorRepository.findByUser(user).isPresent()) {
            throw new NotEligible("This user is already an advisor");
        }
        if (user.getRole() != UserRole.CLIENT) {
            throw new NotEligible("Only a client can be promoted to advisor");
        }
        // the blacklist check is left to the caller

        Advisor advisor = new Advisor();
        advisor.setUser(user);
        advisor.setTitle(req.getTitle());
        advisor.setSpecialty(req.getSpecialty());
        advisor.setYearsOfExperience(req.getYearsOfExperience());
        advisor.setActive(true);
        advisor = advisorRepository.save(advisor);

        user.setRole(UserRole.ADVISOR);
        userRepository.save(user);

        log.info("User {} promoted to advisor {}", user.getId(), advisor.getId());
        return new PromoteAdvisorResponse(advisor.getId(), user.getId());
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new InformationNotFound("User not found");
        }
    }
}
