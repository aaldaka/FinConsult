package com.example.userservice.security;

import com.example.userservice.model.User;
import com.example.userservice.model.enums.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request: if there is a valid JWT, sets the logged in user in the security context.
 * This service owns the users, so the user is loaded from the database on every request: a blacklisted
 * or deactivated account is blocked at once, even with a token that has not expired yet.
 * If there is no valid token, the request stays unauthenticated and gets a 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final MyUserDetailsService myUserDetailsService;
    private final JWTUtils jwtUtils;

    private String parseJwt(HttpServletRequest request) { //sending an authorization header
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); //remove it we need the remaining parts
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                MyUserDetails userDetails = (MyUserDetails) this.myUserDetailsService.loadUserByUsername(username);
                User user = userDetails.getUser();

                if (user.isBlacklisted() || user.getUserStatus() != UserStatus.ACTIVE) {
                    log.warn("Blocked request from user {} (blacklisted: {}, status: {})",
                            user.getId(), user.isBlacklisted(), user.getUserStatus());
                } else {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication", e);
        }
        filterChain.doFilter(request, response);
    }
}
