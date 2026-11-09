package dev.jagadeesh.booking.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {
    List<NotificationLog> findTop50ByTenantIdAndCustomerIdOrderBySentAtDesc(UUID tenantId, UUID customerId);
}
