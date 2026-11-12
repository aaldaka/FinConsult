package com.example.bookingservice.repository;

import com.example.bookingservice.model.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {
    Optional<ServiceType> findByName(String name);

    List<ServiceType> findByActiveTrue();
}
