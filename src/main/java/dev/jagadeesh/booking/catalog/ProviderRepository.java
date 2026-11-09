package dev.jagadeesh.booking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderRepository extends JpaRepository<Provider, UUID> {
    Optional<Provider> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Provider> findByTenantIdAndActiveTrueOrderByName(UUID tenantId);
}
