package com.example.notificationservice.security;

import com.example.notificationservice.service.StreamTicketService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Runs once per request: if there is a valid JWT, the caller from its claims becomes the logged in
 * user. No database call. The notification stream is the one path that can also be opened with a
 * one-time ticket. If neither works the request stays unauthenticated and gets a 401 on protected paths.
 */
@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final String STREAM_PATH = "/notifications/stream";

    private final JWTUtils jwtUtils;
    private final StreamTicketService streamTicketService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            jwtUtils.parse(headerAuth.substring(7)).ifPresent(user ->
                    authenticate(request, user, List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
        } else if (!StringUtils.hasText(headerAuth) && STREAM_PATH.equals(request.getRequestURI())) {
            // sse cant send headers, so the stream takes a one time ticket instead of the token.
            // the ticket only holds the user id, so there is no role and no authority, the stream needs neither
            streamTicketService.consume(request.getParameter("ticket"))
                    .ifPresent(userId -> authenticate(request, new AuthenticatedUser(userId, null), List.of()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, AuthenticatedUser user,
                              Collection<? extends GrantedAuthority> authorities) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
