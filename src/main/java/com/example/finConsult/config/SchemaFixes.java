package com.example.finConsult.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
                CREATE UNIQUE INDEX IF NOT EXISTS ux_advisor_applications_one_pending_per_user
                    ON advisor_applications (user_id)
                    WHERE status = 'PENDING'
                """);

        log.info("Schema fixes applied");

        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_appointments_one_active_per_slot
                    ON appointments (slot_id)
                    WHERE status IN ('PENDING', 'CONFIRMED')
                """);
    }
//only one appointment with PENDING/CONFIRMED can exist, the rest will get conflict 409

}
