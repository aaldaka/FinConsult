package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.InvalidRequest;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.request.CreateSlotRequest;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AvailabilitySlotService { // advisors post their new open slots

    static final String ADVISOR_LOCK_SQL = "SELECT pg_advisory_xact_lock(?)";

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final AdvisorClient advisorClient;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate; // so the transaction in createSlot really applies

    public AvailabilitySlotService(AvailabilitySlotRepository availabilitySlotRepository, AdvisorClient advisorClient,
                                   JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.advisorClient = advisorClient;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AvailabilitySlot createSlot(Long advisorId, CreateSlotRequest request) {
        AdvisorInfo advisor = advisorClient.getById(advisorId); // 404 if unknown, 503 if the user service is down
        if (!advisor.active()) {
            throw new InvalidRequest("This advisor is no longer available");
        }

        return transactionTemplate.execute(status -> {
            jdbcTemplate.queryForList(ADVISOR_LOCK_SQL, advisorId); // waits here if the same advisor is creating a slot

            if (request.getStartingTime().isBefore(LocalDateTime.now())) { //invalid time
                throw new InvalidRequest("Slot start time cannot be in the past.");
            }

            if (!request.getFinishingTime().isAfter(request.getStartingTime())) { //starting and ending time not proper
                throw new InvalidRequest("Slot end time must be after the start time");
            }

            List<AvailabilitySlot> currentSlots = availabilitySlotRepository.findByAdvisorId(advisorId);
            for (AvailabilitySlot slot : currentSlots) { // new proposed slot is in the middle of already posted old slots
                boolean overlapping = slot.getStartTime().isBefore(request.getFinishingTime()) && slot.getEndTime().isAfter(request.getStartingTime());

                if (overlapping) {
                    throw new InvalidRequest("Proposed slot overlap with existing slots");
                }
            }

            AvailabilitySlot newSlot = new AvailabilitySlot();
            newSlot.setAdvisorId(advisorId);
            newSlot.setStartTime(request.getStartingTime());
            newSlot.setEndTime(request.getFinishingTime());
            newSlot.setStatus(SlotStatus.AVAILABLE);
            newSlot = availabilitySlotRepository.save(newSlot);
            log.info("Slot {} created by advisor {}", newSlot.getId(), advisorId);
            return newSlot;
        });
    }

    @Transactional
    public void deleteSlot(Long advisorId, Long slotId) {
        AvailabilitySlot slot = availabilitySlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new InformationNotFound("Slot not found"));

        if (!slot.getAdvisorId().equals(advisorId)) {
            throw new InvalidRequest("You can only delete your own slots");
        }

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new InvalidRequest("Cannot delete a slot that is not available");
        }

        availabilitySlotRepository.delete(slot);
        log.info("Slot {} deleted by advisor {}", slotId, advisorId);
    }

    public List<AvailabilitySlot> getAvailableSlots(Long advisorId) {
        AdvisorInfo advisor = advisorClient.getById(advisorId); // 404 if unknown, asked before the database is touched

        if (!advisor.active()) { // accounts for the advisors that were deleted(inactivated)
            return List.of(); // could also be an exception, however just mark next to the advisor's name as Unavailable
        }

        return availabilitySlotRepository.findByAdvisorIdAndStatus(advisorId, SlotStatus.AVAILABLE);
    }

    public List<AvailabilitySlot> getMySlots(Long advisorId) {
        return availabilitySlotRepository.findByAdvisorId(advisorId);
    }
}
