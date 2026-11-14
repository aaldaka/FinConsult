package com.example.documentservice.repository;

import com.example.documentservice.model.AdvisorApplication;
import com.example.documentservice.model.enums.KycStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface AdvisorApplicationRepository extends JpaRepository<AdvisorApplication, Long> {
    boolean existsByUserIdAndStatus(Long userId, KycStatus status);
    long countByUserId(Long userId);
    List<AdvisorApplication> findByUserId(Long userId);
    Page<AdvisorApplication> findByStatus(KycStatus status, Pageable pageable);

    // bumps the version on commit even if nothing changed, so a review at the same moment conflicts
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    Optional<AdvisorApplication> findLockedById(Long id);
}
