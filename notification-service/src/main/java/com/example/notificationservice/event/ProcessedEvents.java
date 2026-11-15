package com.example.notificationservice.event;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

// remembers which events were handled, so a redelivered event is skipped. Plain jdbc, not a JPA entity: the table is
// created at startup (SchemaFixes) and ddl-auto=validate would fail on an entity whose table does not exist yet
@Repository
@RequiredArgsConstructor
public class ProcessedEvents {

    private final JdbcTemplate jdbcTemplate;

    // true the first time an event id is seen. Run it in the same transaction as the work: if the work fails, the
    // insert is rolled back too and the event is handled again on the retry
    public boolean markProcessed(UUID eventId) {
        return jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, processed_at) VALUES (?, now()) ON CONFLICT (event_id) DO NOTHING",
                eventId) == 1;
    }
}
