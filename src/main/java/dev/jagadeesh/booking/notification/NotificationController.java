package dev.jagadeesh.booking.notification;

import dev.jagadeesh.booking.common.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class NotificationController {

    private final NotificationLogRepository logs;

    public NotificationController(NotificationLogRepository logs) {
        this.logs = logs;
    }

    @GetMapping("/api/notifications")
    public List<NotificationLog> mine(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser me = CurrentUser.of(jwt);
        return logs.findTop50ByTenantIdAndCustomerIdOrderBySentAtDesc(me.tenantId(), me.userId());
    }
}
