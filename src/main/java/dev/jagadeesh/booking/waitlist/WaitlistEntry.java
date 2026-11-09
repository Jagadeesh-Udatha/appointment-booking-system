package dev.jagadeesh.booking.waitlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** "Tell me if anything opens up with this provider on this day." Served first come, first served. */
@Entity
@Table(name = "waitlist_entry")
public class WaitlistEntry {

    public enum Status { WAITING, OFFERED, CLOSED }

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
    @Column(name = "wanted_date", nullable = false)
    private LocalDate wantedDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(UUID tenantId, UUID providerId, UUID serviceId, UUID customerId, LocalDate wantedDate, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.providerId = providerId;
        this.serviceId = serviceId;
        this.customerId = customerId;
        this.wantedDate = wantedDate;
        this.status = Status.WAITING;
        this.createdAt = now;
    }

    void offered() {
        this.status = Status.OFFERED;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProviderId() { return providerId; }
    public LocalDate getWantedDate() { return wantedDate; }
    public Status getStatus() { return status; }
}
