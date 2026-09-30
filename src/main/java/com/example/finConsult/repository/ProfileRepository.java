package com.example.finConsult.repository;

import com.example.finConsult.model.Profile;
import com.example.finConsult.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    Optional<Profile> findByUser(User user); //for metadata like fname and lname
}