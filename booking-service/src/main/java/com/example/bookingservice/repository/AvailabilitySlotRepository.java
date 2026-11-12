package com.example.bookingservice.repository;

import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {
    List<AvailabilitySlot> findByAdvisorIdAndStatus(Long advisorId, SlotStatus status);
    List<AvailabilitySlot> findByAdvisorId(Long advisorId); //checking any slot overlaps

    // a plain value, so no slot entity is left in the persistence context before the locked read
    @Query("select s.advisorId from AvailabilitySlot s where s.id = :id")
    Optional<Long> findAdvisorIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AvailabilitySlot s where s.id = :id")
    Optional<AvailabilitySlot> findByIdForUpdate(@Param("id") Long id);
}
