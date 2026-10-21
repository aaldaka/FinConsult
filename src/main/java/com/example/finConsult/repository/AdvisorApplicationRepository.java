package com.example.finConsult.repository;

import com.example.finConsult.model.AdvisorApplication;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvisorApplicationRepository extends JpaRepository<AdvisorApplication, Long> {
    boolean existsByUserAndStatus(User user, KycStatus status);
    long countByUser(User user);
    List<AdvisorApplication> findByUser(User user);
    Page<AdvisorApplication> findByStatus(KycStatus status, Pageable pageable);
}
