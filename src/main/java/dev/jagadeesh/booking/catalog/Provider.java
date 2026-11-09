package dev.jagadeesh.booking.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Whoever (or whatever) gets booked: a doctor, a technician, a stylist, a meeting room. */
@Entity
@Table(name = "provider")
public class Provider {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String kind;
    @Column(nullable = false)
    private boolean active = true;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "provider_service", joinColumns = @JoinColumn(name = "provider_id"))
    @Column(name = "service_id")
    private Set<UUID> serviceIds = new HashSet<>();

    protected Provider() {
    }

    public Provider(UUID tenantId, String name, String kind, Set<UUID> serviceIds) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.kind = kind;
        this.serviceIds = new HashSet<>(serviceIds);
    }

    public boolean offers(UUID serviceId) {
        return active && serviceIds.contains(serviceId);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public boolean isActive() { return active; }
    public Set<UUID> getServiceIds() { return serviceIds; }
}
