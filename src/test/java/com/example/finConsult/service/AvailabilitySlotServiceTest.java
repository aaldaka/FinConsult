package com.example.finConsult.service;

import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.enums.Specialty;
import com.example.finConsult.model.request.CreateSlotRequest;
import com.example.finConsult.repository.AdvisorRepository;
import com.example.finConsult.repository.AvailabilitySlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilitySlotServiceTest {

    @Mock AvailabilitySlotRepository availabilitySlotRepository;
    @Mock AdvisorRepository advisorRepository;

    @InjectMocks AvailabilitySlotService availabilitySlotService;

    private Advisor newAdvisor(long id) {
        Advisor a = new Advisor();
        a.setId(id);
        a.setSpecialty(Specialty.GENERAL);
        return a;
    }

    private AvailabilitySlot newSlot(Advisor advisor, LocalDateTime start) {
        AvailabilitySlot s = new AvailabilitySlot();
        s.setAdvisor(advisor);
        s.setStartTime(start);
        s.setEndTime(start.plusHours(1));
        return s;
    }

    @Test
    void createSlotInPastFails() {
        Advisor advisor = newAdvisor(1);
        when(advisorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(advisor));
        CreateSlotRequest req = new CreateSlotRequest();
        req.setStartingTime(LocalDateTime.now().minusDays(1));
        req.setFinishingTime(LocalDateTime.now().plusDays(1));

        assertThrows(InvalidRequest.class, () -> availabilitySlotService.createSlot(1L, req));
    }

    @Test
    void createSlotOverlapFails() {
        Advisor advisor = newAdvisor(1);
        AvailabilitySlot existing = newSlot(advisor, LocalDateTime.now().plusDays(1));
        when(advisorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(advisor));
        when(availabilitySlotRepository.findByAdvisor(advisor)).thenReturn(List.of(existing));

        CreateSlotRequest req = new CreateSlotRequest();
        req.setStartingTime(existing.getStartTime().plusMinutes(30));
        req.setFinishingTime(existing.getEndTime().plusMinutes(30));

        assertThrows(InvalidRequest.class, () -> availabilitySlotService.createSlot(1L, req));
    }
}
