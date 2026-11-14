package com.example.documentservice.repository;

import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    boolean existsByUserIdAndStatus(Long userId, KycStatus status); // has any approved one
    List<KycDocument> findByUserId(Long userId);
    Page<KycDocument> findByStatus(KycStatus status, Pageable pageable);
    boolean existsByUserIdAndDocumentTypeAndStatus(Long userId, DocumentType type, KycStatus status);
    Page<KycDocument> findByStatusAndDocumentType(KycStatus status, DocumentType documentType, Pageable pageable);
    boolean existsBySha256AndUserIdNot(String hash, Long userId); // the same file from another account
}
