package com.example.finConsult.controller;

import com.example.finConsult.security.MyUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test") //testing jwt token verifications
public class TestController {

    @GetMapping("/protected")
    public String protectedEndpoint(@AuthenticationPrincipal UserDetails userDetails) {
        MyUserDetails myUserDetails = (MyUserDetails) userDetails;
        return "You are authenticated as: " + myUserDetails.getUser().getEmail()
                + " (role: " + myUserDetails.getUser().getRole() + ")";
    }
}