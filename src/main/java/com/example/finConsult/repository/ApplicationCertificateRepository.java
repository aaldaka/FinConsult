package com.example.finConsult.repository;

import com.example.finConsult.model.AdvisorApplication;
import com.example.finConsult.model.ApplicationCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationCertificateRepository extends JpaRepository<ApplicationCertificate, Long> {
    List<ApplicationCertificate> findByApplication(AdvisorApplication application);
    boolean existsBySha256AndIdNot(String sha256, Long id);
}
