package com.example.notificationservice.event;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

// end to end on an embedded broker, with the real listener on audit-events: a valid AUDIT message becomes an
// audit_logs row, and a message that is not json is moved unchanged, with its key, to audit-events.DLT. Runs on
// finconsult_test (TEST_DB_URL); the row and the processed_events entry are deleted afterwards
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {AuditEventConsumer.TOPIC, AuditEventConsumer.TOPIC + ".DLT"})
@TestPropertySource(locations = "classpath:test.properties", properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true"
})
class AuditEventDltTest {

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    private final long entityId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
    private final UUID eventId = UUID.randomUUID();

    @AfterEach
    void deleteWhatThisTestCreated() {
        jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_id = ?", entityId);
        jdbcTemplate.update("DELETE FROM processed_events WHERE event_id = ?", eventId);
    }

    private long rows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?", Long.class, entityId);
    }

    @Test
    void aValidAuditMessageBecomesARow() throws Exception {
        String message = "{\"eventId\": \"" + eventId + "\", \"type\": \"AUDIT\", \"occurredAt\": \"2026-11-15T10:30:00Z\", "
                + "\"source\": \"booking-service\", \"payload\": {\"action\": \"APPOINTMENT_BOOKED\", \"entityType\": \"Appointment\", "
                + "\"entityId\": " + entityId + ", \"actorId\": null, \"details\": \"booked\"}}";

        kafkaTemplate.send(AuditEventConsumer.TOPIC, "Appointment:" + entityId, message).get();

        long deadline = System.currentTimeMillis() + 30_000;
        while (rows() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertEquals(1, rows());
    }

    @Test
    void aMessageThatCannotBeHandledEndsUpInTheDlt() throws Exception {
        kafkaTemplate.send(AuditEventConsumer.TOPIC, "Appointment:42", "this is not json").get();

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "audit-dlt-reader");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> reader = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(reader, AuditEventConsumer.TOPIC + ".DLT");
            ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(reader,
                    AuditEventConsumer.TOPIC + ".DLT", Duration.ofSeconds(30));

            assertEquals("Appointment:42", dead.key());
            assertEquals("this is not json", dead.value());
        }
    }
}
