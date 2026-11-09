package dev.jagadeesh.booking.common;

import java.time.Instant;
import java.util.UUID;

/** Everything other parts of the system (and other systems) may react to. Published on booking.events. */
public final class BookingEvents {
    private BookingEvents() {
    }

    public record BookingConfirmed(UUID bookingId, UUID tenantId, UUID customerId, UUID providerId, Instant startAt) {
    }

    public record BookingCancelled(UUID bookingId, UUID tenantId, UUID customerId, UUID providerId, Instant startAt, String reason) {
    }

    public record BookingRescheduled(UUID bookingId, UUID tenantId, UUID customerId, Instant oldStartAt, Instant newStartAt) {
    }

    public record ReminderDue(UUID bookingId, UUID tenantId, UUID customerId, Instant startAt) {
    }

    public record WaitlistSlotOpened(UUID waitlistId, UUID tenantId, UUID customerId, UUID providerId, Instant startAt) {
    }
}
