package dev.jagadeesh.booking.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * write(): call inside the business transaction. publish(): the relay, sends committed rows to Kafka keyed by
 * aggregate id (the booking), at-least-once, so consumers de-duplicate on the event-id header.
 */
@Component
public class Outbox {

    public static final String TOPIC = "booking.events";
    private static final Logger log = LoggerFactory.getLogger(Outbox.class);

    private final OutboxRepository rows;
    private final ObjectMapper json;
    private final KafkaTemplate<String, String> kafka;
    private final boolean relayEnabled;

    public Outbox(OutboxRepository rows, ObjectMapper json, KafkaTemplate<String, String> kafka,
                  @Value("${booking.outbox.relay-enabled:true}") boolean relayEnabled) {
        this.rows = rows;
        this.json = json;
        this.kafka = kafka;
        this.relayEnabled = relayEnabled;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(Object aggregateId, Object event) {
        try {
            rows.save(new OutboxEvent(aggregateId.toString(), event.getClass().getSimpleName(), json.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Scheduled(fixedDelayString = "${booking.outbox.poll-ms:500}")
    @Transactional
    public int publish() {
        if (!relayEnabled) {
            return 0;
        }
        int sent = 0;
        for (OutboxEvent e : rows.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                var record = new ProducerRecord<>(TOPIC, e.getAggregateId(), e.getPayload());
                record.headers().add("event-id", e.getId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add("event-type", e.getEventType().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(5, TimeUnit.SECONDS);
                e.published();
                sent++;
            } catch (Exception ex) {
                log.warn("Outbox publish failed for {}, retrying next poll: {}", e.getId(), ex.getMessage());
                break;
            }
        }
        return sent;
    }
}
