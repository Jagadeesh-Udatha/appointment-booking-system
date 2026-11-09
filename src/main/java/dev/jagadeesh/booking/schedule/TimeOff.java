package dev.jagadeesh.booking.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Leave, a training day, a broken machine: nothing can be booked in [startAt, endAt). */
@Entity
@Table(name = "time_off")
public class TimeOff {
    @Id
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at", nullable = false)
    private Instant endAt;
    private String reason;

    protected TimeOff() {
    }

    public TimeOff(UUID providerId, Instant startAt, Instant endAt, String reason) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("End must be after start");
        }
        this.id = UUID.randomUUID();
        this.providerId = providerId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
    }

    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public String getReason() { return reason; }
}
