package com.example.finConsult.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration //to let the java know this is the bean that has the executable code
@EnableMethodSecurity(prePostEnabled = true) //itll  be checked before and after a method, and we can add (role == "sth) to add more stuff
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtRequestFilter jwtRequestFilter;

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean //required
    public SecurityFilterChain filterChain(HttpSecurity http)throws Exception{
        http.csrf(csrf -> csrf.disable()) //if backend and frontend on diff domain, we need to enable it for cross sites
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                        "/auth/login",
                                        "/auth/register",
                                        "/auth/forget-password",
                                        "/auth/verify-email"
                                ).permitAll()
                                .anyRequest().authenticated()
                );

        //inserting the jwt filtering into the chain somewhere before giving an OK to the system to accept the user
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }


}
