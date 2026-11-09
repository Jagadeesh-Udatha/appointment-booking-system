package dev.jagadeesh.booking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {
    Optional<ServiceType> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ServiceType> findByTenantIdOrderByName(UUID tenantId);

    long countByTenantIdAndIdIn(UUID tenantId, java.util.Collection<UUID> ids);
}
