package com.example.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// creates the processed_events table the kafka consumer uses to skip duplicates. It runs while the beans are created,
// before the kafka listener starts, so the table is there before the first message (an ApplicationRunner would run
// only after the listener had started). IF NOT EXISTS makes it safe on every start
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaFixes implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS processed_events (
                    event_id uuid PRIMARY KEY,
                    processed_at timestamp with time zone NOT NULL
                )
                """);
        log.info("Schema fixes applied");
    }
}
