package dev.jagadeesh.booking.tenant;

import dev.jagadeesh.booking.common.CurrentUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String email;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrentUser.Role role;

    protected AppUser() {
    }

    public AppUser(UUID tenantId, String email, String fullName, String passwordHash, CurrentUser.Role role) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.email = email.toLowerCase();
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public CurrentUser.Role getRole() { return role; }
}
