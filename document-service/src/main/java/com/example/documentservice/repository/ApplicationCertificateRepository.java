package com.example.documentservice.repository;

import com.example.documentservice.model.AdvisorApplication;
import com.example.documentservice.model.ApplicationCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationCertificateRepository extends JpaRepository<ApplicationCertificate, Long> {
    List<ApplicationCertificate> findByApplication(AdvisorApplication application);
    boolean existsBySha256AndApplication_UserIdNot(String sha256, Long userId); // the same file from another applicant
    boolean existsBySha256AndApplicationAndIdNot(String sha256, AdvisorApplication application, Long id); // twice in one application
}
