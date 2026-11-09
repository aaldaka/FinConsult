package com.example.finConsult.service;

import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.SlotStatus;
import com.example.finConsult.model.request.CreateSlotRequest;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Races inside the appointment lifecycle
class AppointmentConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired AppointmentService appointmentService;
    @Autowired AvailabilitySlotService availabilitySlotService;

    // the advisor confirms while the client cancels (@Version on Appointment)
    @RepeatedTest(15)
    void confirmWhileCancelling() throws Exception {
        Advisor advisor = seededAdvisor();
        ServiceType service = freeService();
        AvailabilitySlot slot = newFutureSlot(advisor);
        User client = newClient();
        Appointment appointment = appointmentService.bookSlot(client, slot.getId(), service.getId());

        Result result = runAtOnce(List.of(
                () -> appointmentService.confirmAppointment(advisor.getId(), appointment.getId()),
                () -> appointmentService.cancelAppointment(appointment.getId(), client, "changed my mind")));

        AppointmentStatus status = appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus();
        SlotStatus slotStatus = availabilitySlotRepository.findById(slot.getId()).orElseThrow().getStatus();

        assertEquals(0, result.other(), "no unexpected errors");
        assertTrue(status == AppointmentStatus.CONFIRMED || status == AppointmentStatus.CANCELLED,
                "final status must be one of the two, was " + status);
        // the slot must always match the appointment: cancelled -> free again, confirmed -> still booked
        assertEquals(status == AppointmentStatus.CANCELLED ? SlotStatus.AVAILABLE : SlotStatus.BOOKED, slotStatus);
    }

    // the advisor posts two overlapping slots at the same moment: only one may be created
    @RepeatedTest(10)
    void overlappingSlotsCreatedAtOnce() throws Exception {
        Advisor advisor = seededAdvisor();
        // a start time nobody has used: the day after the advisor's latest slot ends, or after now + 5 years if that is later.
        // A random day can already hold a slot from an earlier run, and then both new requests are refused
        LocalDateTime latestEnd = availabilitySlotRepository.findByAdvisor(advisor).stream()
                .map(AvailabilitySlot::getEndTime)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.MIN);
        LocalDateTime floor = LocalDateTime.now().plusYears(5);
        LocalDateTime start = (latestEnd.isAfter(floor) ? latestEnd : floor).plusDays(1);

        CreateSlotRequest first = new CreateSlotRequest();
        first.setStartingTime(start);
        first.setFinishingTime(start.plusHours(1));
        CreateSlotRequest second = new CreateSlotRequest();
        second.setStartingTime(start.plusMinutes(30));      // overlaps the first
        second.setFinishingTime(start.plusHours(2));

        Result result = runAtOnce(List.of(
                () -> availabilitySlotService.createSlot(advisor.getId(), first),
                () -> availabilitySlotService.createSlot(advisor.getId(), second)));

        long created = availabilitySlotRepository.findByAdvisor(advisor).stream()
                .filter(s -> s.getStartTime().isAfter(start.minusMinutes(1)) && s.getStartTime().isBefore(start.plusHours(2)))
                .count();

        assertEquals(0, result.other());
        assertEquals(1, result.successes());
        assertEquals(1, result.refused());
        assertEquals(1, created, "only one of the two overlapping slots may exist");
    }
}
