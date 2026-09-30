package com.example.finConsult.repository;

import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.enums.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvisorRepository extends JpaRepository<Advisor, Long> {
    List<Advisor> findBySpecialtyAndActiveTrue(Specialty specialty);
}