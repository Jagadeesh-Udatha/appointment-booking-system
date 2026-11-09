package dev.jagadeesh.booking;

import dev.jagadeesh.booking.catalog.CatalogService;
import dev.jagadeesh.booking.catalog.Provider;
import dev.jagadeesh.booking.catalog.ServiceType;
import dev.jagadeesh.booking.common.CurrentUser;
import dev.jagadeesh.booking.schedule.ScheduleService;
import dev.jagadeesh.booking.tenant.AccountService;
import dev.jagadeesh.booking.tenant.Tenant;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/** A clinic in India: one doctor, 09:00-13:00 every day, 30-minute consultation + 10-minute buffer, 2-hour cancel cut-off. */
public class Fixture {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final LocalDate TUESDAY = LocalDate.of(2030, 1, 8);

    public final Tenant tenant;
    public final ServiceType consult;
    public final Provider doctor;
    public final CurrentUser admin;

    public Fixture(AccountService accounts, CatalogService catalog, ScheduleService schedule) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        tenant = accounts.createTenant("City Clinic " + tag, "Asia/Kolkata", 120, 10, "admin-" + tag + "@clinic.test", "Admin", "password-123");
        admin = new CurrentUser(UUID.randomUUID(), tenant.getId(), CurrentUser.Role.ADMIN);
        consult = catalog.addService(tenant.getId(), "General consultation", 30, 10, new BigDecimal("500"));
        doctor = catalog.addProvider(tenant.getId(), "Dr. Rao", "Doctor", Set.of(consult.getId()));
        schedule.setWeeklyHours(tenant.getId(), doctor.getId(), Arrays.stream(DayOfWeek.values())
                .map(d -> new ScheduleService.Hours(d, LocalTime.of(9, 0), LocalTime.of(13, 0))).toList());
    }

    public CurrentUser customer(AccountService accounts) {
        var u = accounts.addUser(tenant.getId(), "c-" + UUID.randomUUID() + "@mail.test", "Customer", "password-123", CurrentUser.Role.CUSTOMER);
        return new CurrentUser(u.getId(), tenant.getId(), CurrentUser.Role.CUSTOMER);
    }

    public static Instant at(LocalDate day, int h, int m) {
        return ZonedDateTime.of(day, LocalTime.of(h, m), IST).toInstant();
    }
}
