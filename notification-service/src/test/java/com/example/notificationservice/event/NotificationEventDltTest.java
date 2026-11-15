package com.example.notificationservice.event;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

// end to end on an embedded broker: a message that is not json is put on notification-events, the listener fails on
// it and the error handler moves it, unchanged and with its key, to notification-events.DLT
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {NotificationEventConsumer.TOPIC, NotificationEventConsumer.TOPIC + ".DLT"})
@TestPropertySource(locations = "classpath:test.properties", properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true"
})
class NotificationEventDltTest {

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void aMessageThatCannotBeHandledEndsUpInTheDlt() throws Exception {
        kafkaTemplate.send(NotificationEventConsumer.TOPIC, "14", "this is not json").get();

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> reader = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(reader, NotificationEventConsumer.TOPIC + ".DLT");
            ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(reader,
                    NotificationEventConsumer.TOPIC + ".DLT", Duration.ofSeconds(30));

            assertEquals("14", dead.key());
            assertEquals("this is not json", dead.value());
        }
    }
}
