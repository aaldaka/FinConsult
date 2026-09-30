//package com.example.finConsult.security;
//
//import com.example.finConsult.model.User;
//import lombok.AllArgsConstructor;
//import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.stereotype.Service;
//
//import java.security.Security;
//
//
//@Service
//@AllArgsConstructor
//public class MyUserDetailsService implements UserDetailsService {
//
//    private UserService userService;
//
//    @Override
//    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
//        User user = userService.findUserByEmailAddress(email);
//        return new MyUserDetails(user);
//    }
//}
//
