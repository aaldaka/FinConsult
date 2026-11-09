package com.example.userservice.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the currently authenticated user's ID to Spring Data JPA auditing
 * (e.g. {@code @CreatedBy}/{@code @LastModifiedBy} fields).
 */
@Component("auditorAware")
public class SecurityAuditorAware implements AuditorAware<Long> {

    /**
     * @return the current user's ID, or empty if there is no authenticated user
     */
    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof MyUserDetails details)) {
            return Optional.empty();
        }
        return Optional.of(details.getUser().getId());
    }
}
