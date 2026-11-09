package com.example.userservice.repository;

import com.example.userservice.model.Advisor;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.Specialty;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdvisorRepository extends JpaRepository<Advisor, Long> {
    List<Advisor> findBySpecialtyAndActiveTrue(Specialty specialty);
    Optional<Advisor> findByUser(User user);
    List<Advisor> findByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Advisor a where a.id = :id")
    Optional<Advisor> findByIdForUpdate(@Param("id") Long id);
}
