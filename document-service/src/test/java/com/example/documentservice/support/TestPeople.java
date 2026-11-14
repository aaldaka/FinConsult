package com.example.documentservice.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// users made only for one test, with unique emails, so a test never reaches rows another test or the main app's tests
// own. deleteCreated removes their documents, applications and certificates first, then the users
public class TestPeople {

    private final JdbcTemplate jdbcTemplate;
    private final List<Long> createdUserIds = new ArrayList<>();

    public TestPeople(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private long newUser(String role) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO users (email, password, role, user_status, blacklisted, no_show_strikes, created_at, updated_at) "
                        + "VALUES (?, 'not-a-real-hash', ?, 'ACTIVE', false, 0, now(), now()) RETURNING id",
                Long.class, "documents-test-" + UUID.randomUUID() + "@test.com", role);
        createdUserIds.add(id);
        return id;
    }

    public long newClient() {
        return newUser("CLIENT");
    }

    public long newAdmin() {
        return newUser("ADMIN");
    }

    public void deleteCreated() {
        for (Long id : createdUserIds) {
            jdbcTemplate.update("DELETE FROM application_certificates WHERE application_id IN "
                    + "(SELECT id FROM advisor_applications WHERE user_id = ?)", id);
            jdbcTemplate.update("DELETE FROM advisor_applications WHERE user_id = ?", id);
            jdbcTemplate.update("DELETE FROM kyc_documents WHERE user_id = ?", id);
        }
        for (Long id : createdUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
        }
        createdUserIds.clear();
    }
}
