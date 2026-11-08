package com.example.finConsult.security;

import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.UserStatus;
import com.example.finConsult.service.StreamTicketService;
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
 * Runs once per request: if there is a valid JWT, sets the logged in user in the
 * security context. If not, the request stays unauthenticated and gets a 401.
 */
@Slf4j
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private MyUserDetailsService myUserDetailsService;

    @Autowired
    private JWTUtils jwtUtils;

    @Autowired
    private StreamTicketService streamTicketService;

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
            String username = null;
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                username = jwtUtils.getUserNameFromJwtToken(jwt);
            } else if (jwt == null && "/notifications/stream".equals(request.getRequestURI())) {
                //sse cant send headers, so the stream takes a one time ticket instead of the token
                username = streamTicketService.consume(request.getParameter("ticket")).orElse(null);
            }

            if (username != null) {
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
