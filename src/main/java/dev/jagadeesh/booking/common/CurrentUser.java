package dev.jagadeesh.booking.common;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Who is calling and for which business (tenant). The tenant always comes from the signed token, never from the
 * request body or URL, so one business can never read or book into another's data.
 */
public record CurrentUser(UUID userId, UUID tenantId, Role role) {

    public enum Role { ADMIN, STAFF, CUSTOMER }

    public static CurrentUser of(Jwt jwt) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("tenant")),
                Role.valueOf(jwt.getClaimAsString("role")));
    }

    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.STAFF;
    }

    public void requireAdmin() {
        if (role != Role.ADMIN) {
            throw new AccessDeniedException("Only the business admin can do this");
        }
    }
}
