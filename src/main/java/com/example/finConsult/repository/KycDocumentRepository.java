package com.example.finConsult.repository;

import com.example.finConsult.model.KycDocument;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    boolean existsByUserAndStatus(User user, KycStatus status); //has an approved one
}