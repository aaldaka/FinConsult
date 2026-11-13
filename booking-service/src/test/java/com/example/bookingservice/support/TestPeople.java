package com.example.bookingservice.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// clients and advisors made only for one test, with unique emails, because the cascades cancel every active appointment
// of a person and must never reach rows another test or the main app's tests own. Call deleteCreated after
// SlotTestData.deleteCreated, the appointments and slots point at these rows
public class TestPeople {

    private final JdbcTemplate jdbcTemplate;
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdAdvisorIds = new ArrayList<>();

    public TestPeople(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private long newUser(String role) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO users (email, password, role, user_status, blacklisted, no_show_strikes, created_at, updated_at) "
                        + "VALUES (?, 'not-a-real-hash', ?, 'ACTIVE', false, 0, now(), now()) RETURNING id",
                Long.class, "booking-internal-" + UUID.randomUUID() + "@test.com", role);
        createdUserIds.add(id);
        return id;
    }

    public long newClient() {
        return newUser("CLIENT");
    }

    public record Advisor(long id, long userId) {}

    public Advisor newAdvisor() {
        long userId = newUser("ADVISOR");
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO advisors (user_id, title, specialty, active, years_of_experience, created_at, updated_at) "
                        + "VALUES (?, 'Planner', 'GENERAL', true, 1, now(), now()) RETURNING id",
                Long.class, userId);
        createdAdvisorIds.add(id);
        return new Advisor(id, userId);
    }

    public void deleteCreated() {
        for (Long id : createdAdvisorIds) {
            jdbcTemplate.update("DELETE FROM advisors WHERE id = ?", id);
        }
        for (Long id : createdUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
        }
        createdAdvisorIds.clear();
        createdUserIds.clear();
    }
}
