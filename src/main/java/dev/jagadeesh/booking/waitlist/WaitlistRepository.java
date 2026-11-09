package dev.jagadeesh.booking.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {
    Optional<WaitlistEntry> findFirstByTenantIdAndProviderIdAndServiceIdAndWantedDateAndStatusOrderByCreatedAtAsc(
            UUID tenantId, UUID providerId, UUID serviceId, LocalDate date, WaitlistEntry.Status status);

    boolean existsByCustomerIdAndProviderIdAndWantedDateAndStatus(UUID customerId, UUID providerId, LocalDate date, WaitlistEntry.Status status);
}
