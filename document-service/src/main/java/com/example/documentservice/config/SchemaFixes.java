package com.example.documentservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// the partial unique indexes JPA cannot declare: one pending KYC document per user and type, and one pending advisor
// application per user. They are the backstop for a double submit. Same names and definitions as the main app, and
// IF NOT EXISTS, so it is harmless when the main app has already created them on the shared database
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaFixes implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_kyc_one_pending_per_user_and_type
                    ON kyc_documents (user_id, document_type)
                    WHERE status = 'PENDING'
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_advisor_applications_one_pending_per_user
                    ON advisor_applications (user_id)
                    WHERE status = 'PENDING'
                """);
        log.info("Schema fixes applied");
    }
}
