package com.example.finConsult.service;

import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.ServiceType;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The main scenario: many clients, one slot. Also slot creation and deletion racing with booking.
class BookingConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired AppointmentService appointmentService;
    @Autowired AvailabilitySlotService availabilitySlotService;

    private long activeAppointments(Advisor advisor, Long slotId) {
        return appointmentRepository.findByAdvisorAndStatusIn(advisor,
                        List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)).stream()
                .filter(a -> a.getSlot().getId().equals(slotId))
                .count();
    }

    // two clients press "Book" on the same slot at the same moment
    @RepeatedTest(20)
    void twoClientsOneSlot() throws Exception {
        Advisor advisor = seededAdvisor();
        ServiceType service = freeService();
        AvailabilitySlot slot = newFutureSlot(advisor);
        User first = newClient();
        User second = newClient();

        Result result = runAtOnce(List.of(
                () -> appointmentService.bookSlot(first, slot.getId(), service.getId()),
                () -> appointmentService.bookSlot(second, slot.getId(), service.getId())));

        assertEquals(0, result.other(), "no unexpected errors");
        assertEquals(1, result.successes(), "exactly one booking should win");
        assertEquals(1, result.refused(), "the other one must be refused");
        assertEquals(1, activeAppointments(advisor, slot.getId()), "one active appointment for the slot");
    }

    // eight clients at once: one winner, seven refused
    @RepeatedTest(5)
    void eightClientsOneSlot() throws Exception {
        Advisor advisor = seededAdvisor();
        ServiceType service = freeService();
        AvailabilitySlot slot = newFutureSlot(advisor);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            User client = newClient();
            tasks.add(() -> appointmentService.bookSlot(client, slot.getId(), service.getId()));
        }

        Result result = runAtOnce(tasks);

        assertEquals(0, result.other());
        assertEquals(1, result.successes());
        assertEquals(7, result.refused());
        assertEquals(1, activeAppointments(advisor, slot.getId()));
    }

    // the advisor deletes the slot while a client books it: only one of the two may win
    @RepeatedTest(10)
    void deleteSlotWhileBooking() throws Exception {
        Advisor advisor = seededAdvisor();
        ServiceType service = freeService();
        AvailabilitySlot slot = newFutureSlot(advisor);
        User client = newClient();

        Result result = runAtOnce(List.of(
                () -> appointmentService.bookSlot(client, slot.getId(), service.getId()),
                () -> availabilitySlotService.deleteSlot(advisor.getId(), slot.getId())));

        boolean slotStillExists = availabilitySlotRepository.findById(slot.getId()).isPresent();

        assertEquals(0, result.other());
        assertEquals(1, result.successes(), "either the booking or the delete wins, never both");
        assertEquals(1, result.refused());
        // booked slot -> one appointment and the slot exists; deleted slot -> no appointment
        assertEquals(slotStillExists ? 1 : 0, activeAppointments(advisor, slot.getId()));
    }
}
