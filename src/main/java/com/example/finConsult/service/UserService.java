package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.UserStatus;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AdvisorService advisorService;

    @Transactional
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InformationNotFound("User not found"));

        user.setUserStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }

    @PatchMapping("/advisors/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivateAdvisor(@PathVariable Long id, @RequestBody ReasonRequest request) {
        advisorService.deactivateAdvisor(id, request.getReason());
        return ResponseEntity.ok(new MessageResponse("Advisor deactivated"));
    }

}
