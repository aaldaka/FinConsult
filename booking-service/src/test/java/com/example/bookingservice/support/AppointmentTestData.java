package com.example.bookingservice.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

// changes only the rows a test created, by id. The slots and the appointments on them are deleted again by SlotTestData
public class AppointmentTestData {

    private final JdbcTemplate jdbcTemplate;

    public AppointmentTestData(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void moveSlot(long slotId, LocalDateTime start, LocalDateTime end) {
        jdbcTemplate.update("UPDATE availability_slots SET start_time = ?, end_time = ? WHERE id = ?",
                Timestamp.valueOf(start), Timestamp.valueOf(end), slotId);
    }

    // started 30 minutes ago, so the 20 minute grace for a no-show has passed, and not over for another 90 minutes,
    // so the completion job leaves it alone
    public void startedHalfAnHourAgo(long slotId) {
        moveSlot(slotId, LocalDateTime.now().minusMinutes(30), LocalDateTime.now().plusMinutes(90));
    }

    public void setStatus(long appointmentId, String status) {
        jdbcTemplate.update("UPDATE appointments SET status = ? WHERE id = ?", status, appointmentId);
    }

    public boolean isExcused(long appointmentId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT excused FROM appointments WHERE id = ?", Boolean.class, appointmentId));
    }
}
