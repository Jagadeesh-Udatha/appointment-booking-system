package dev.jagadeesh.booking.waitlist;

import dev.jagadeesh.booking.catalog.CatalogService;
import dev.jagadeesh.booking.common.BookingEvents;
import dev.jagadeesh.booking.common.CurrentUser;
import dev.jagadeesh.booking.common.Outbox;
import dev.jagadeesh.booking.common.RuleViolation;
import dev.jagadeesh.booking.tenant.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class WaitlistService {

    private final WaitlistRepository entries;
    private final CatalogService catalog;
    private final AccountService accounts;
    private final Outbox outbox;
    private final Clock clock;

    public WaitlistService(WaitlistRepository entries, CatalogService catalog, AccountService accounts, Outbox outbox, Clock clock) {
        this.entries = entries;
        this.catalog = catalog;
        this.accounts = accounts;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public WaitlistEntry join(CurrentUser me, UUID providerId, UUID serviceId, LocalDate date) {
        catalog.provider(me.tenantId(), providerId);
        catalog.service(me.tenantId(), serviceId);
        if (entries.existsByCustomerIdAndProviderIdAndWantedDateAndStatus(me.userId(), providerId, date, WaitlistEntry.Status.WAITING)) {
            throw new RuleViolation("ALREADY_WAITING", "You are already on the waitlist for that day");
        }
        return entries.save(new WaitlistEntry(me.tenantId(), providerId, serviceId, me.userId(), date, clock.instant()));
    }

    /**
     * Runs inside the cancel/reschedule transaction: the first person waiting for that provider, service and day is
     * told the slot is free. The slot is not reserved for them; the notification links to the normal booking flow.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void offerFreedSlot(UUID tenantId, UUID providerId, UUID serviceId, Instant start) {
        LocalDate date = start.atZone(accounts.tenant(tenantId).zone()).toLocalDate();
        entries.findFirstByTenantIdAndProviderIdAndServiceIdAndWantedDateAndStatusOrderByCreatedAtAsc(
                        tenantId, providerId, serviceId, date, WaitlistEntry.Status.WAITING)
                .ifPresent(e -> {
                    e.offered();
                    outbox.write(e.getId(), new BookingEvents.WaitlistSlotOpened(e.getId(), tenantId, e.getCustomerId(), providerId, start));
                });
    }
}
