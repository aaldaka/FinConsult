package com.example.bookingservice.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

// read-only lookups of rows that already exist in finconsult_test, because appointments have foreign keys to them.
// Nothing here inserts, updates or deletes
public class BookingLookups {

    private static final String NEED_MAIN_TESTS = "Run the main app's tests first (./mvnw test in the project root), they create it.";

    private final JdbcTemplate jdbcTemplate;

    public BookingLookups(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // existing clients; if the database has fewer than wanted, they are reused round and round
    public List<Long> clientIds(int wanted) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM users WHERE role = 'CLIENT' ORDER BY id LIMIT ?", Long.class, wanted);
        assertFalse(ids.isEmpty(), "Need a client in finconsult_test. " + NEED_MAIN_TESTS);
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < wanted; i++) {
            result.add(ids.get(i % ids.size()));
        }
        return result;
    }

    public long aClientId() {
        return clientIds(1).get(0);
    }

    // an active service that needs no KYC
    public long freeServiceTypeId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM service_types WHERE active = true AND requires_kyc = false ORDER BY id LIMIT 1", Long.class);
        assertFalse(ids.isEmpty(), "Need an active service type without KYC in finconsult_test. " + NEED_MAIN_TESTS);
        return ids.get(0);
    }

    public long userIdOfAdvisor(long advisorId) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM advisors WHERE id = ?", Long.class, advisorId);
    }

    public String statusOfAppointment(long appointmentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, appointmentId);
    }

    public String statusOfSlot(long slotId) {
        return jdbcTemplate.queryForObject("SELECT status FROM availability_slots WHERE id = ?", String.class, slotId);
    }

    public long activeAppointmentsOnSlot(long slotId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM appointments WHERE slot_id = ? AND status IN ('PENDING', 'CONFIRMED')", Long.class, slotId);
    }
}
