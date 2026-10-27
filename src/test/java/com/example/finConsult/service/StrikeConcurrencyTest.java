package com.example.finConsult.service;

import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.SlotStatus;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The strike counter: atomic SQL, blacklist at 3, and excusing a no-show only once
class StrikeConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired AppointmentService appointmentService;

    // many strikes at the same moment: none may be lost, and every caller gets its own number back
    @RepeatedTest(5)
    void parallelStrikesAreNotLost() throws Exception {
        User client = newClient();
        Long id = client.getId();
        int strikes = 10;
        Set<Integer> returned = ConcurrentHashMap.newKeySet();

        Result result = runAtOnce(strikes, () ->
                returned.add(transaction().execute(status -> userRepository.addStrike(id))));

        User stored = userRepository.findById(id).orElseThrow();
        assertEquals(0, result.other());
        assertEquals(strikes, stored.getNoShowStrikes(), "no strike may be lost");
        assertEquals(strikes, returned.size(), "every caller must get a different count back");
        assertTrue(stored.isBlacklisted());
    }

    @Test
    void blacklistedExactlyAtThreeStrikes() throws Exception {
        User client = newClient();
        Long id = client.getId();

        transaction().execute(status -> userRepository.addStrike(id));
        transaction().execute(status -> userRepository.addStrike(id));
        assertEquals(false, userRepository.findById(id).orElseThrow().isBlacklisted(), "2 strikes: not blacklisted");

        transaction().execute(status -> userRepository.addStrike(id));
        assertEquals(true, userRepository.findById(id).orElseThrow().isBlacklisted(), "3 strikes: blacklisted");
    }

    // two admins excuse the same no-show at once: the strike may only be removed once
    @RepeatedTest(10)
    void excuseNoShowTwiceAtOnce() throws Exception {
        Advisor advisor = seededAdvisor();
        User client = newClient();
        User admin = admin();

        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setAdvisor(advisor);
        slot.setStartTime(LocalDateTime.now().minusHours(3));   // already started, so a no-show can be marked
        slot.setEndTime(LocalDateTime.now().minusHours(2));
        slot.setStatus(SlotStatus.BOOKED);
        slot = availabilitySlotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setAdvisor(advisor);
        appointment.setSlot(slot);
        appointment.setServiceType(freeService());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment = appointmentRepository.save(appointment);

        client.setNoShowStrikes(1);
        userRepository.save(client);
        Long appointmentId = appointment.getId();

        appointmentService.markNoShow(advisor.getId(), appointmentId);   // strikes 1 -> 2
        assertEquals(2, userRepository.findById(client.getId()).orElseThrow().getNoShowStrikes());

        Result result = runAtOnce(2, () -> appointmentService.excuseNoShow(appointmentId, "test reason", admin));

        assertEquals(0, result.other());
        assertEquals(1, result.successes(), "only one excuse may go through");
        assertEquals(1, result.refused());
        assertEquals(1, userRepository.findById(client.getId()).orElseThrow().getNoShowStrikes(),
                "the strike must be removed once (2 -> 1), not twice");
        assertTrue(appointmentRepository.findById(appointmentId).orElseThrow().isExcused());
    }
}
