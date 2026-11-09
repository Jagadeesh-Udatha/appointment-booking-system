package dev.jagadeesh.booking.booking;

import dev.jagadeesh.booking.catalog.CatalogService;
import dev.jagadeesh.booking.catalog.Provider;
import dev.jagadeesh.booking.catalog.ServiceType;
import dev.jagadeesh.booking.common.BookingEvents;
import dev.jagadeesh.booking.common.CurrentUser;
import dev.jagadeesh.booking.common.NotFoundException;
import dev.jagadeesh.booking.common.Outbox;
import dev.jagadeesh.booking.common.RuleViolation;
import dev.jagadeesh.booking.schedule.ScheduleService;
import dev.jagadeesh.booking.schedule.SlotFinder;
import dev.jagadeesh.booking.tenant.AccountService;
import dev.jagadeesh.booking.tenant.Tenant;
import dev.jagadeesh.booking.waitlist.WaitlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Double booking is stopped by the database, not by a check in code: every booking inserts one row per 5-minute
 * unit it covers into booked_unit, which has a unique key on (provider_id, unit_start). Two customers racing for
 * the same or overlapping time both try to insert the same unit; one insert fails and that whole transaction rolls
 * back. This works the same with one app instance or ten.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    static final Duration REMINDER_AHEAD = Duration.ofHours(24);

    private final BookingRepository bookings;
    private final CatalogService catalog;
    private final ScheduleService schedule;
    private final AccountService accounts;
    private final WaitlistService waitlist;
    private final Outbox outbox;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BookingService(BookingRepository bookings, CatalogService catalog, ScheduleService schedule, AccountService accounts,
                          WaitlistService waitlist, Outbox outbox, JdbcTemplate jdbc, Clock clock) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.schedule = schedule;
        this.accounts = accounts;
        this.waitlist = waitlist;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Booking hold(CurrentUser me, UUID providerId, UUID serviceId, Instant start) {
        Provider provider = catalog.provider(me.tenantId(), providerId);
        ServiceType service = catalog.service(me.tenantId(), serviceId);
        if (!provider.offers(serviceId)) {
            throw new RuleViolation("SERVICE_NOT_OFFERED", provider.getName() + " does not offer " + service.getName());
        }
        if (!schedule.isBookable(me.tenantId(), providerId, serviceId, start)) {
            throw new RuleViolation("SLOT_UNAVAILABLE", "That time is not available");
        }
        Tenant tenant = accounts.tenant(me.tenantId());
        Instant now = clock.instant();
        Booking b = bookings.saveAndFlush(new Booking(me.tenantId(), providerId, serviceId, me.userId(), start,
                start.plus(service.duration()), start.plus(service.blocked()), now.plus(tenant.hold()), now));
        lockUnits(b);
        return b;
    }

    @Transactional
    public Booking confirm(CurrentUser me, UUID bookingId) {
        Booking b = visible(me, bookingId);
        Instant now = clock.instant();
        if (b.getStatus() != BookingStatus.HELD || b.getHoldExpiresAt().isBefore(now)) {
            throw new RuleViolation("HOLD_NOT_ACTIVE", "This hold has expired or was already used; pick the slot again");
        }
        b.moveTo(BookingStatus.CONFIRMED, null, now);
        outbox.write(b.getId(), new BookingEvents.BookingConfirmed(b.getId(), b.getTenantId(), b.getCustomerId(), b.getProviderId(), b.getStartAt()));
        return b;
    }

    @Transactional
    public Booking cancel(CurrentUser me, UUID bookingId, String reason) {
        Booking b = visible(me, bookingId);
        if (!b.getStatus().holdsTime()) {
            throw new RuleViolation("NOT_CANCELLABLE", "Booking is already " + b.getStatus());
        }
        checkCutoff(me, b, "cancel");
        Instant now = clock.instant();
        b.moveTo(BookingStatus.CANCELLED, reason, now);
        releaseUnits(b);
        outbox.write(b.getId(), new BookingEvents.BookingCancelled(b.getId(), b.getTenantId(), b.getCustomerId(), b.getProviderId(), b.getStartAt(), reason));
        waitlist.offerFreedSlot(b.getTenantId(), b.getProviderId(), b.getServiceId(), b.getStartAt());
        return b;
    }

    /** All in one transaction: if the new time is taken, the old units come back with the rollback. */
    @Transactional
    public Booking reschedule(CurrentUser me, UUID bookingId, Instant newStart) {
        Booking b = visible(me, bookingId);
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw new RuleViolation("NOT_RESCHEDULABLE", "Only confirmed bookings can be moved");
        }
        checkCutoff(me, b, "move");
        Instant oldStart = b.getStartAt();
        releaseUnits(b);
        if (!schedule.isBookable(me.tenantId(), b.getProviderId(), b.getServiceId(), newStart)) {
            throw new RuleViolation("SLOT_UNAVAILABLE", "That time is not available");
        }
        ServiceType service = catalog.service(me.tenantId(), b.getServiceId());
        b.moveTime(newStart, newStart.plus(service.duration()), newStart.plus(service.blocked()), clock.instant());
        bookings.saveAndFlush(b);
        lockUnits(b);
        outbox.write(b.getId(), new BookingEvents.BookingRescheduled(b.getId(), b.getTenantId(), b.getCustomerId(), oldStart, newStart));
        waitlist.offerFreedSlot(b.getTenantId(), b.getProviderId(), b.getServiceId(), oldStart);
        return b;
    }

    /** Front desk marks what happened after the start time. */
    @Transactional
    public Booking recordAttendance(CurrentUser me, UUID bookingId, boolean attended) {
        if (!me.isStaff()) {
            throw new org.springframework.security.access.AccessDeniedException("Staff only");
        }
        Booking b = visible(me, bookingId);
        Instant now = clock.instant();
        if (b.getStatus() != BookingStatus.CONFIRMED || now.isBefore(b.getStartAt())) {
            throw new RuleViolation("TOO_EARLY", "Attendance can be recorded only for a confirmed booking after it starts");
        }
        b.moveTo(attended ? BookingStatus.COMPLETED : BookingStatus.NO_SHOW, null, now);
        return b;
    }

    @Transactional(readOnly = true)
    public List<Booking> dayView(CurrentUser me, UUID providerId, LocalDate date) {
        if (!me.isStaff()) {
            throw new org.springframework.security.access.AccessDeniedException("Staff only");
        }
        Tenant t = accounts.tenant(me.tenantId());
        return bookings.dayView(me.tenantId(), providerId, date.atStartOfDay(t.zone()).toInstant(),
                date.plusDays(1).atStartOfDay(t.zone()).toInstant());
    }

    /** Customers see their own bookings, staff see the whole business. Anything else is "not found". */
    public Booking visible(CurrentUser me, UUID bookingId) {
        Booking b = bookings.findByIdAndTenantId(bookingId, me.tenantId()).orElseThrow(() -> new NotFoundException("Booking " + bookingId));
        if (!me.isStaff() && !b.getCustomerId().equals(me.userId())) {
            throw new NotFoundException("Booking " + bookingId);
        }
        return b;
    }

    @Scheduled(fixedDelayString = "${booking.jobs.hold-expiry-ms:30000}")
    @Transactional
    public int expireHolds() {
        Instant now = clock.instant();
        List<Booking> expired = bookings.expiredHolds(now);
        for (Booking b : expired) {
            b.moveTo(BookingStatus.EXPIRED, "Not confirmed in time", now);
            releaseUnits(b);
        }
        if (!expired.isEmpty()) {
            log.info("Released {} expired holds", expired.size());
        }
        return expired.size();
    }

    @Scheduled(fixedDelayString = "${booking.jobs.reminder-ms:60000}")
    @Transactional
    public int sendReminders() {
        Instant now = clock.instant();
        List<Booking> due = bookings.remindersDue(now, now.plus(REMINDER_AHEAD));
        for (Booking b : due) {
            b.reminded(now);
            outbox.write(b.getId(), new BookingEvents.ReminderDue(b.getId(), b.getTenantId(), b.getCustomerId(), b.getStartAt()));
        }
        return due.size();
    }

    private void checkCutoff(CurrentUser me, Booking b, String action) {
        if (me.isStaff()) {
            return;
        }
        Tenant t = accounts.tenant(me.tenantId());
        if (clock.instant().plus(t.cancelCutoff()).isAfter(b.getStartAt())) {
            throw new RuleViolation("TOO_LATE", "Bookings can't be " + (action.equals("cancel") ? "cancelled" : "moved")
                    + " within " + t.cancelCutoff().toHours() + " hours of the start; please call the front desk");
        }
    }

    private void lockUnits(Booking b) {
        List<Instant> units = SlotFinder.units(b.getStartAt(), b.getBlockedUntil());
        try {
            jdbc.batchUpdate("insert into booked_unit (provider_id, unit_start, booking_id) values (?, ?, ?)", units, units.size(),
                    (ps, u) -> {
                        ps.setString(1, b.getProviderId().toString());
                        ps.setTimestamp(2, Timestamp.from(u));
                        ps.setString(3, b.getId().toString());
                    });
        } catch (DataIntegrityViolationException taken) {
            throw new RuleViolation("SLOT_TAKEN", "Someone else just booked this time; please pick another slot");
        }
    }

    private void releaseUnits(Booking b) {
        jdbc.update("delete from booked_unit where booking_id = ?", b.getId().toString());
    }
}
