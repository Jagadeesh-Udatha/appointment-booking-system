package dev.jagadeesh.booking.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Primary key = Kafka event id, so a redelivered event can't produce a second SMS/email. */
@Entity
@Table(name = "notification_log")
public class NotificationLog {
    @Id
    @Column(name = "event_id")
    private String eventId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private String message;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected NotificationLog() {
    }

    public NotificationLog(String eventId, UUID tenantId, UUID customerId, String type, String message, Instant sentAt) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.type = type;
        this.message = message;
        this.sentAt = sentAt;
    }

    public String getEventId() { return eventId; }
    public UUID getCustomerId() { return customerId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Instant getSentAt() { return sentAt; }
}
