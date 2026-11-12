package com.example.bookingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// the booking part of the monolith's SchemaFixes, safe to run on every start: drops the old unique constraint on
// appointments.slot_id and keeps one PENDING or CONFIRMED appointment per slot (the backstop for a double booking)
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaFixes implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                DO $$
                DECLARE
                    old_constraint text;
                BEGIN
                    SELECT tc.constraint_name INTO old_constraint
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                      ON tc.constraint_name = kcu.constraint_name
                     AND tc.table_schema = kcu.table_schema
                    WHERE tc.table_name = 'appointments'
                      AND tc.constraint_type = 'UNIQUE'
                      AND kcu.column_name = 'slot_id'
                    LIMIT 1;

                    IF old_constraint IS NOT NULL THEN
                        EXECUTE format('ALTER TABLE appointments DROP CONSTRAINT %I', old_constraint);
                    END IF;
                END $$;
                """);

        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_appointments_one_active_per_slot
                    ON appointments (slot_id)
                    WHERE status IN ('PENDING', 'CONFIRMED')
                """);

        log.info("Booking schema fixes applied");
    }
}
