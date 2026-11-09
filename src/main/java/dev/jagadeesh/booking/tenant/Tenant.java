package dev.jagadeesh.booking.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;

/** A business using the platform: a clinic, a service centre, a salon. Its rules live here, not in code. */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String timezone;
    /** customers cannot cancel or move a booking closer than this to its start */
    @Column(name = "cancel_cutoff_minutes", nullable = false)
    private int cancelCutoffMinutes;
    /** how long a held slot waits for confirmation */
    @Column(name = "hold_minutes", nullable = false)
    private int holdMinutes;

    protected Tenant() {
    }

    public Tenant(String name, String timezone, int cancelCutoffMinutes, int holdMinutes) {
        ZoneId.of(timezone); // fail fast on a bad zone
        this.id = UUID.randomUUID();
        this.name = name;
        this.timezone = timezone;
        this.cancelCutoffMinutes = cancelCutoffMinutes;
        this.holdMinutes = holdMinutes;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public ZoneId zone() { return ZoneId.of(timezone); }
    public Duration cancelCutoff() { return Duration.ofMinutes(cancelCutoffMinutes); }
    public Duration hold() { return Duration.ofMinutes(holdMinutes); }
}
