package dev.jagadeesh.booking.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jagadeesh.booking.common.Outbox;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class BookingEventsListener {

    private final NotificationService notifications;
    private final ObjectMapper json;

    public BookingEventsListener(NotificationService notifications, ObjectMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @KafkaListener(topics = Outbox.TOPIC, groupId = "booking-notifications")
    public void on(ConsumerRecord<String, String> record) throws JsonProcessingException {
        notifications.handle(header(record, "event-id"), header(record, "event-type"), json.readTree(record.value()));
    }

    private static String header(ConsumerRecord<?, ?> r, String name) {
        return new String(r.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
