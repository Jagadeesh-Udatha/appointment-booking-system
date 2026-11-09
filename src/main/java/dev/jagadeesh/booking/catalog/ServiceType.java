package dev.jagadeesh.booking.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/** What is booked: "General consultation, 15 min", "AC service, 60 min + 15 min travel buffer". */
@Entity
@Table(name = "service_type")
public class ServiceType {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    /** clean-up / travel time after the appointment, blocked but not shown to the customer */
    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected ServiceType() {
    }

    public ServiceType(UUID tenantId, String name, int durationMinutes, int bufferMinutes, BigDecimal price) {
        if (durationMinutes % 5 != 0 || bufferMinutes % 5 != 0) {
            throw new IllegalArgumentException("Duration and buffer must be multiples of 5 minutes");
        }
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
        this.price = price;
    }

    public Duration duration() { return Duration.ofMinutes(durationMinutes); }
    public Duration blocked() { return Duration.ofMinutes(durationMinutes + bufferMinutes); }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getBufferMinutes() { return bufferMinutes; }
    public BigDecimal getPrice() { return price; }
}
