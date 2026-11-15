package com.example.notificationservice.config;

import com.example.notificationservice.event.InvalidEventException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.mail.MailSendException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// no broker: the real error handler is driven by hand and the dead letter producer is kafka's MockProducer, which
// keeps what was sent. One handleOne call stands for one failed delivery of the record
@ExtendWith(OutputCaptureExtension.class)
class KafkaConsumerConfigTest {

    private MockProducer<String, String> producer;
    private DefaultErrorHandler errorHandler;
    private MessageListenerContainer container;
    private Consumer<?, ?> consumer;

    private final ConsumerRecord<String, String> record =
            new ConsumerRecord<>("notification-events", 1, 7L, "14", "{\"eventId\": \"...\"}");

    @BeforeEach
    void setUp() {
        producer = new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
        errorHandler = KafkaConsumerConfig.deadLetterErrorHandler(new KafkaTemplate<>(() -> producer));
        container = mock(MessageListenerContainer.class);
        when(container.isRunning()).thenReturn(true);
        when(container.getContainerProperties()).thenReturn(new ContainerProperties("notification-events"));
        consumer = mock(Consumer.class);
    }

    private boolean fail(Exception e) {
        return fail(e, record);
    }

    private boolean fail(Exception e, ConsumerRecord<String, String> failed) {
        return errorHandler.handleOne(e, failed, consumer, container);
    }

    @Test
    void aFailingRecordIsRetriedThreeTimesThenSentToTheDltWithItsKey() {
        RuntimeException failure = new IllegalStateException("database down");

        assertFalse(fail(failure), "first delivery: retry");
        assertFalse(fail(failure), "retry 1");
        assertFalse(fail(failure), "retry 2");
        assertTrue(producer.history().isEmpty(), "nothing in the DLT while retries are left");
        assertTrue(fail(failure), "retry 3 fails too, so the record is recovered");

        List<ProducerRecord<String, String>> sent = producer.history();
        assertEquals(1, sent.size());
        assertEquals("notification-events.DLT", sent.get(0).topic());
        assertEquals("14", sent.get(0).key());
        assertEquals(record.value(), sent.get(0).value());
    }

    @Test
    void anInvalidEventGoesToTheDltWithoutRetries() {
        assertTrue(fail(new InvalidEventException("The message is not a valid event")));

        assertEquals(1, producer.history().size());
        assertEquals("notification-events.DLT", producer.history().get(0).topic());
    }

    @Test
    void aFailingEmailIsRetriedThenSentToTheEmailDltWithoutLoggingItsLink(CapturedOutput output) {
        String token = UUID.randomUUID().toString();
        ConsumerRecord<String, String> email = new ConsumerRecord<>("email-events", 0, 3L, "client@example.com",
                "{\"payload\": {\"data\": {\"link\": \"http://localhost:5173/reset-password?token=" + token + "\"}}}");
        RuntimeException failure = new MailSendException("smtp unavailable");

        assertFalse(fail(failure, email));
        assertFalse(fail(failure, email));
        assertFalse(fail(failure, email));
        assertTrue(fail(failure, email), "the 4th failure sends it to the DLT");

        assertEquals(1, producer.history().size());
        assertEquals("email-events.DLT", producer.history().get(0).topic());
        assertEquals("client@example.com", producer.history().get(0).key());
        assertEquals(email.value(), producer.history().get(0).value());
        assertFalse(output.getAll().contains(token), "the error handler logged the token");
    }
}
