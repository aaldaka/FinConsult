package com.example.finConsult.repository;

import com.example.finConsult.model.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {
    Optional<ServiceType> findByName(String name);

    List<ServiceType> findByActiveTrue();
}
