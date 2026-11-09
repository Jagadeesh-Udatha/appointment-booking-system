package dev.jagadeesh.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jagadeesh.booking.notification.NotificationLogRepository;
import dev.jagadeesh.booking.notification.NotificationService;
import dev.jagadeesh.booking.tenant.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class NotificationTest {

    @Autowired NotificationService notifications;
    @Autowired NotificationLogRepository logs;
    @Autowired AccountService accounts;
    @Autowired ObjectMapper json;

    @Test
    void messageUsesTheBusinessTimeZoneAndIsSentOnce() throws Exception {
        var tenant = accounts.createTenant("Smile Dental", "Asia/Kolkata", 60, 10, "o-" + UUID.randomUUID() + "@d.test", "Owner", "password-123");
        UUID customer = UUID.randomUUID();
        var event = json.readTree("{\"bookingId\":\"" + UUID.randomUUID() + "\",\"tenantId\":\"" + tenant.getId()
                + "\",\"customerId\":\"" + customer + "\",\"startAt\":\"2030-01-08T04:30:00Z\"}");
        String eventId = "evt-" + UUID.randomUUID();

        assertThat(notifications.handle(eventId, "BookingConfirmed", event)).isTrue();
        assertThat(notifications.handle(eventId, "BookingConfirmed", event)).isFalse();

        assertThat(logs.findById(eventId).orElseThrow().getMessage()).isEqualTo("Your appointment is booked for Tue 8 Jan, 10:00 am.");
    }
}
