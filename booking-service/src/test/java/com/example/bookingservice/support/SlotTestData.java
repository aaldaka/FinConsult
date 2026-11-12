package com.example.bookingservice.support;

import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class SlotTestData {

    private final JdbcTemplate jdbcTemplate;
    private final AvailabilitySlotRepository slotRepository;
    private final List<Long> createdSlotIds = new ArrayList<>();

    public SlotTestData(JdbcTemplate jdbcTemplate, AvailabilitySlotRepository slotRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.slotRepository = slotRepository;
    }

    public long anAdvisorId() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM advisors ORDER BY id LIMIT 1", Long.class);
        assertFalse(ids.isEmpty(), "Need an advisor in finconsult_test. "
                + "Run the main app's tests first (./mvnw test in the project root), they create the advisors.");
        return ids.get(0);
    }

    public LocalDateTime freeStart(long advisorId) {
        Timestamp latest = jdbcTemplate.queryForObject(
                "SELECT max(end_time) FROM availability_slots WHERE advisor_id = ?", Timestamp.class, advisorId);
        LocalDateTime floor = LocalDateTime.now().plusYears(5);
        LocalDateTime latestEnd = latest == null ? LocalDateTime.MIN : latest.toLocalDateTime();
        return (latestEnd.isAfter(floor) ? latestEnd : floor).plusDays(1);
    }

    public AvailabilitySlot createSlot(long advisorId, SlotStatus status) {
        LocalDateTime start = freeStart(advisorId);
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setAdvisorId(advisorId);
        slot.setStartTime(start);
        slot.setEndTime(start.plusHours(1));
        slot.setStatus(status);
        slot = slotRepository.save(slot);
        createdSlotIds.add(slot.getId());
        return slot;
    }

    public void track(Long slotId) {
        createdSlotIds.add(slotId);
    }

    public void trackSlotsStartingBetween(long advisorId, LocalDateTime from, LocalDateTime to) {
        createdSlotIds.addAll(jdbcTemplate.queryForList(
                "SELECT id FROM availability_slots WHERE advisor_id = ? AND start_time >= ? AND start_time <= ?",
                Long.class, advisorId, Timestamp.valueOf(from), Timestamp.valueOf(to)));
    }

    public void deleteCreated() {
        for (Long id : createdSlotIds) {
            jdbcTemplate.update("DELETE FROM appointments WHERE slot_id = ?", id); // only those booked on this test's slots
            jdbcTemplate.update("DELETE FROM availability_slots WHERE id = ?", id);
        }
        createdSlotIds.clear();
    }
}
