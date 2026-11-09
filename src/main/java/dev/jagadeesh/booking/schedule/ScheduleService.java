package dev.jagadeesh.booking.schedule;

import dev.jagadeesh.booking.catalog.CatalogService;
import dev.jagadeesh.booking.catalog.Provider;
import dev.jagadeesh.booking.catalog.ServiceType;
import dev.jagadeesh.booking.tenant.AccountService;
import dev.jagadeesh.booking.tenant.Tenant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ScheduleService {

    public static final Duration STEP = Duration.ofMinutes(15);

    public record Hours(DayOfWeek day, LocalTime start, LocalTime end) {
    }

    private final WorkingHoursRepository hours;
    private final TimeOffRepository timeOff;
    private final CatalogService catalog;
    private final AccountService accounts;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ScheduleService(WorkingHoursRepository hours, TimeOffRepository timeOff, CatalogService catalog, AccountService accounts,
                           JdbcTemplate jdbc, Clock clock) {
        this.hours = hours;
        this.timeOff = timeOff;
        this.catalog = catalog;
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Replaces the provider's whole weekly timetable. */
    @Transactional
    public void setWeeklyHours(UUID tenantId, UUID providerId, List<Hours> week) {
        catalog.provider(tenantId, providerId);
        hours.deleteByProviderId(providerId);
        hours.flush();
        week.forEach(h -> hours.save(new WorkingHours(providerId, h.day(), h.start(), h.end())));
    }

    @Transactional
    public TimeOff addTimeOff(UUID tenantId, UUID providerId, Instant from, Instant to, String reason) {
        catalog.provider(tenantId, providerId);
        return timeOff.save(new TimeOff(providerId, from, to, reason));
    }

    @Transactional(readOnly = true)
    public List<Instant> slots(UUID tenantId, UUID providerId, UUID serviceId, LocalDate date) {
        Provider p = catalog.provider(tenantId, providerId);
        ServiceType s = catalog.service(tenantId, serviceId);
        if (!p.offers(serviceId)) {
            return List.of();
        }
        Tenant t = accounts.tenant(tenantId);
        Instant dayStart = date.atStartOfDay(t.zone()).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(t.zone()).toInstant();
        List<SlotFinder.Window> windows = hours.findByProviderIdAndDayOfWeekOrderByStartTime(providerId, date.getDayOfWeek().getValue())
                .stream().map(h -> new SlotFinder.Window(h.getStartTime(), h.getEndTime())).toList();
        List<SlotFinder.Interval> off = timeOff.overlapping(providerId, dayStart, dayEnd).stream()
                .map(o -> new SlotFinder.Interval(o.getStartAt(), o.getEndAt())).toList();
        Set<Instant> busy = new HashSet<>(jdbc.query(
                "select unit_start from booked_unit where provider_id = ? and unit_start >= ? and unit_start < ?",
                (rs, i) -> rs.getTimestamp(1).toInstant(), providerId.toString(), Timestamp.from(dayStart), Timestamp.from(dayEnd)));
        return SlotFinder.find(date, t.zone(), windows, off, busy, s.blocked(), STEP, clock.instant());
    }

    /** True when start is one of the slots the customer could have seen. Used before taking a hold. */
    public boolean isBookable(UUID tenantId, UUID providerId, UUID serviceId, Instant start) {
        LocalDate date = start.atZone(accounts.tenant(tenantId).zone()).toLocalDate();
        return slots(tenantId, providerId, serviceId, date).contains(start);
    }
}
