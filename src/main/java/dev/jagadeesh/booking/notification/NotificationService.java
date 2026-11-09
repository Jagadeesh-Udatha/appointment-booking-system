package dev.jagadeesh.booking.notification;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jagadeesh.booking.tenant.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Turns booking events into customer messages, in the business's own time zone. Runs off Kafka, so a slow SMS
 * provider never slows down booking itself.
 */
@Service
public class NotificationService {

    // built by hand so the text is the same on every machine, whatever the JVM locale is
    private static final DateTimeFormatter WHEN = new DateTimeFormatterBuilder()
            .appendPattern("EEE d MMM, h:mm ")
            .appendText(ChronoField.AMPM_OF_DAY, Map.of(0L, "am", 1L, "pm"))
            .toFormatter(Locale.ENGLISH);

    private final NotificationLogRepository logs;
    private final MessageSender sender;
    private final AccountService accounts;
    private final Clock clock;

    public NotificationService(NotificationLogRepository logs, MessageSender sender, AccountService accounts, Clock clock) {
        this.logs = logs;
        this.sender = sender;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public boolean handle(String eventId, String type, JsonNode e) {
        if (logs.existsById(eventId)) {
            return false;
        }
        UUID tenantId = UUID.fromString(e.path("tenantId").asText());
        var zone = accounts.tenant(tenantId).zone();
        String message = switch (type) {
            case "BookingConfirmed" -> "Your appointment is booked for " + when(e, "startAt", zone) + ".";
            case "BookingCancelled" -> "Your appointment on " + when(e, "startAt", zone) + " was cancelled.";
            case "BookingRescheduled" -> "Your appointment moved to " + when(e, "newStartAt", zone) + ".";
            case "ReminderDue" -> "Reminder: you have an appointment on " + when(e, "startAt", zone) + ".";
            case "WaitlistSlotOpened" -> "A slot opened on " + when(e, "startAt", zone) + ". Book it before someone else does.";
            default -> null;
        };
        if (message == null) {
            return false;
        }
        UUID customer = UUID.fromString(e.path("customerId").asText());
        logs.saveAndFlush(new NotificationLog(eventId, tenantId, customer, type, message, clock.instant()));
        sender.send(customer, message);
        return true;
    }

    private static String when(JsonNode e, String field, java.time.ZoneId zone) {
        return WHEN.format(Instant.parse(e.path(field).asText()).atZone(zone));
    }
}
