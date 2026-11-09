package dev.jagadeesh.booking.catalog;

import dev.jagadeesh.booking.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;
    private final ProviderRepository providers;
    private final ServiceTypeRepository services;

    public CatalogController(CatalogService catalog, ProviderRepository providers, ServiceTypeRepository services) {
        this.catalog = catalog;
        this.providers = providers;
        this.services = services;
    }

    public record NewService(@NotBlank String name, @Min(5) @Max(480) int durationMinutes, @Min(0) @Max(120) int bufferMinutes,
                             @NotNull @DecimalMin("0") BigDecimal price) {
    }

    public record NewProvider(@NotBlank String name, @NotBlank String kind, @NotEmpty Set<UUID> serviceIds) {
    }

    public record ProviderView(UUID id, String name, String kind, Set<UUID> serviceIds) {
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceType addService(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewService r) {
        CurrentUser me = CurrentUser.of(jwt);
        me.requireAdmin();
        return catalog.addService(me.tenantId(), r.name(), r.durationMinutes(), r.bufferMinutes(), r.price());
    }

    @GetMapping("/services")
    public List<ServiceType> services(@AuthenticationPrincipal Jwt jwt) {
        return services.findByTenantIdOrderByName(CurrentUser.of(jwt).tenantId());
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderView addProvider(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewProvider r) {
        CurrentUser me = CurrentUser.of(jwt);
        me.requireAdmin();
        Provider p = catalog.addProvider(me.tenantId(), r.name(), r.kind(), r.serviceIds());
        return new ProviderView(p.getId(), p.getName(), p.getKind(), p.getServiceIds());
    }

    @GetMapping("/providers")
    public List<ProviderView> providers(@AuthenticationPrincipal Jwt jwt) {
        return providers.findByTenantIdAndActiveTrueOrderByName(CurrentUser.of(jwt).tenantId()).stream()
                .map(p -> new ProviderView(p.getId(), p.getName(), p.getKind(), p.getServiceIds())).toList();
    }
}
