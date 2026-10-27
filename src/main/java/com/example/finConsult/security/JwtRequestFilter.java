package com.example.finConsult.security;

import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request: if a valid Bearer JWT is present, populates the Spring
 * Security context so the rest of the chain sees an authenticated user. Silently
 * leaves the request unauthenticated on any failure, letting the security filter
 * chain's own 401/403 handling take over downstream.
 */
@Slf4j
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private MyUserDetailsService myUserDetailsService;

    @Autowired
    private JWTUtils jwtUtils;

    private String parseJwt(HttpServletRequest request) { //sending an authorization header
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7, headerAuth.length()); //remove it we need the remaining parts
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
