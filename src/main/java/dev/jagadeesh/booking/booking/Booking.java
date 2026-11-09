package dev.jagadeesh.booking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking")
public class Booking {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    /** end of the appointment the customer sees */
    @Column(name = "end_at", nullable = false)
    private Instant endAt;
    /** end including the buffer; units are locked up to here */
    @Column(name = "blocked_until", nullable = false)
    private Instant blockedUntil;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;
    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;
    @Column(name = "status_reason")
    private String statusReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Booking() {
    }

    Booking(UUID tenantId, UUID providerId, UUID serviceId, UUID customerId, Instant startAt, Instant endAt, Instant blockedUntil,
            Instant holdExpiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.providerId = providerId;
        this.serviceId = serviceId;
        this.customerId = customerId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.blockedUntil = blockedUntil;
        this.status = BookingStatus.HELD;
        this.holdExpiresAt = holdExpiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void moveTo(BookingStatus next, String reason, Instant now) {
        this.status = next;
        this.statusReason = reason;
        this.updatedAt = now;
        if (next != BookingStatus.HELD) {
            this.holdExpiresAt = null;
        }
    }

    void moveTime(Instant start, Instant end, Instant blockedUntil, Instant now) {
        this.startAt = start;
        this.endAt = end;
        this.blockedUntil = blockedUntil;
        this.reminderSentAt = null;
        this.updatedAt = now;
    }

    void reminded(Instant now) {
        this.reminderSentAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProviderId() { return providerId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getCustomerId() { return customerId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public BookingStatus getStatus() { return status; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public String getStatusReason() { return statusReason; }
}
