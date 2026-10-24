package com.example.finConsult.repository;

import com.example.finConsult.model.AdvisorApplication;
import com.example.finConsult.model.ApplicationCertificate;
import com.example.finConsult.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationCertificateRepository extends JpaRepository<ApplicationCertificate, Long> {
    List<ApplicationCertificate> findByApplication(AdvisorApplication application);
    boolean existsBySha256AndApplication_UserNot(String sha256, User user);
    boolean existsBySha256AndApplicationAndIdNot(String sha256, AdvisorApplication application, Long id);}
