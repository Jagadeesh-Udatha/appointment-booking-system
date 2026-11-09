package dev.jagadeesh.booking.catalog;

import dev.jagadeesh.booking.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Service
public class CatalogService {

    private final ProviderRepository providers;
    private final ServiceTypeRepository services;

    public CatalogService(ProviderRepository providers, ServiceTypeRepository services) {
        this.providers = providers;
        this.services = services;
    }

    @Transactional
    public ServiceType addService(UUID tenantId, String name, int duration, int buffer, BigDecimal price) {
        return services.save(new ServiceType(tenantId, name, duration, buffer, price));
    }

    @Transactional
    public Provider addProvider(UUID tenantId, String name, String kind, Set<UUID> serviceIds) {
        if (services.countByTenantIdAndIdIn(tenantId, serviceIds) != serviceIds.size()) {
            throw new IllegalArgumentException("Every service must belong to this business");
        }
        return providers.save(new Provider(tenantId, name, kind, serviceIds));
    }

    /** Tenant-scoped lookups: an id from another business is simply "not found". */
    public Provider provider(UUID tenantId, UUID id) {
        return providers.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Provider " + id));
    }

    public ServiceType service(UUID tenantId, UUID id) {
        return services.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Service " + id));
    }
}
