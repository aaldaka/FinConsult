package com.example.finConsult.repository;

import com.example.finConsult.model.KycDocument;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    boolean existsByUserAndStatus(User user, KycStatus status); //has an approved one
    List<KycDocument> findByUser(User user);
    Page<KycDocument> findByStatus(KycStatus status, Pageable pageable);
    boolean existsByUserAndDocumentTypeAndStatus(User u, DocumentType t, KycStatus status);
    Page<KycDocument> findByStatusAndDocumentType(KycStatus status, DocumentType documentType, Pageable pageable);
    boolean existsBySha256AndIdNot(String sha256, Long id);
}