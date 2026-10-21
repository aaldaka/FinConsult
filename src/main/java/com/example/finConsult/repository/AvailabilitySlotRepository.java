package com.example.finConsult.repository;

import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.enums.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {
    List<AvailabilitySlot> findByAdvisorAndStatus(Advisor advisor, SlotStatus status);
    List<AvailabilitySlot> findByAdvisor(Advisor advisor); //checking any slot overlaps

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AvailabilitySlot s where s.id = :id")
    Optional<AvailabilitySlot> findByIdForUpdate(@Param("id") Long id);
}
