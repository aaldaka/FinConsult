package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.enums.SlotStatus;
import com.example.finConsult.model.request.CreateSlotRequest;
import com.example.finConsult.repository.AdvisorRepository;
import com.example.finConsult.repository.AvailabilitySlotRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Manages advisor-posted availability slots: creation (with overlap checking),
 * deletion, and lookups for clients browsing an advisor's availability.
 */
@Slf4j
@Service
@AllArgsConstructor
public class AvailabilitySlotService { // advisors post their new open slots

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final AdvisorRepository advisorRepository;

    /** Rejects slots in the past, backwards ranges, and anything overlapping the advisor's existing slots. */
    public AvailabilitySlot createSlot(Long advisorId, CreateSlotRequest request) {
        Advisor advisor = advisorRepository.findById(advisorId)
                .orElseThrow(() -> new InformationNotFound("Advisor: " + advisorId + " does not exist."));

        if (request.getStartingTime().isBefore(LocalDateTime.now())){ //invalid time
            throw new InvalidRequest("Slot start time cannot be in the past.");
        }

        if (!request.getFinishingTime().isAfter(request.getStartingTime())) { //starting and ending time not proper
            throw new InvalidRequest("Slot end time must be after the start time");
        }

        List<AvailabilitySlot> currentSlots = availabilitySlotRepository.findByAdvisor(advisor);
        for (AvailabilitySlot slot :currentSlots){ // new proposed slot is in the middle of already posted old slots
            boolean overlapping = slot.getStartTime().isBefore(request.getFinishingTime()) && slot.getEndTime().isAfter(request.getStartingTime());

            if (overlapping){
                throw new InvalidRequest("Proposed slot overlap with existing slots");
            }
        }

        AvailabilitySlot newSlot = new AvailabilitySlot();
        newSlot.setAdvisor(advisor);
        newSlot.setStartTime(request.getStartingTime());
        newSlot.setEndTime(request.getFinishingTime());
        newSlot.setStatus(SlotStatus.AVAILABLE);
        newSlot = availabilitySlotRepository.save(newSlot);
        log.info("Slot {} created by advisor {}", newSlot.getId(), advisorId);
        return newSlot;
    }

    /** Only the owning advisor may delete a slot, and only while it's unbooked. */
    public void deleteSlot(Long advisorId, Long slotId) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new InformationNotFound("Slot not found"));

        if (!slot.getAdvisor().getId().equals(advisorId)) {
            throw new InvalidRequest("You can only delete your own slots");
        }

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new InvalidRequest("Cannot delete a slot that is not available");
        }

        availabilitySlotRepository.delete(slot);
        log.info("Slot {} deleted by advisor {}", slotId, advisorId);
    }

    /** @return the advisor's available slots, or empty if the advisor is inactive */
    public List<AvailabilitySlot> getAvailableSlots(Long advisorId) {
        Advisor advisor = advisorRepository.findById(advisorId)
                .orElseThrow(() -> new InformationNotFound("Advisor not found"));

        if (!advisor.isActive()) { // accounts for the advisors that were deleted(inactivated)
            return List.of(); // could also be an exception, however just mark next to the advisor's name as Unavailable
        }

        return availabilitySlotRepository.findByAdvisorAndStatus(advisor, SlotStatus.AVAILABLE);
    }

    /** @return every slot the advisor has posted, regardless of status */
    public List<AvailabilitySlot> getMySlots(Long advisorId) {
        Advisor advisor = advisorRepository.findById(advisorId)
                .orElseThrow(() -> new InformationNotFound("Advisor not found"));

        return availabilitySlotRepository.findByAdvisor(advisor);
    }
}