package dev.jagadeesh.booking;

import dev.jagadeesh.booking.booking.Booking;
import dev.jagadeesh.booking.booking.BookingService;
import dev.jagadeesh.booking.booking.BookingStatus;
import dev.jagadeesh.booking.catalog.CatalogService;
import dev.jagadeesh.booking.common.CurrentUser;
import dev.jagadeesh.booking.common.NotFoundException;
import dev.jagadeesh.booking.common.OutboxEvent;
import dev.jagadeesh.booking.common.OutboxRepository;
import dev.jagadeesh.booking.common.RuleViolation;
import dev.jagadeesh.booking.schedule.ScheduleService;
import dev.jagadeesh.booking.tenant.AccountService;
import dev.jagadeesh.booking.waitlist.WaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static dev.jagadeesh.booking.Fixture.TUESDAY;
import static dev.jagadeesh.booking.Fixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class BookingFlowTest {

    @Autowired BookingService bookings;
    @Autowired ScheduleService schedule;
    @Autowired AccountService accounts;
    @Autowired CatalogService catalog;
    @Autowired WaitlistService waitlist;
    @Autowired OutboxRepository outbox;
    @Autowired MutableClock clock;

    Fixture clinic;

    @BeforeEach
    void setUp() {
        clock.set(TestClockConfig.START);
        clinic = new Fixture(accounts, catalog, schedule);
    }

    Booking book(CurrentUser who, Instant start) {
        Booking held = bookings.hold(who, clinic.doctor.getId(), clinic.consult.getId(), start);
        return bookings.confirm(who, held.getId());
    }

    @Test
    void holdThenConfirmPublishesBookingConfirmed() {
        Booking b = book(clinic.customer(accounts), at(TUESDAY, 10, 0));

        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.getEndAt()).isEqualTo(at(TUESDAY, 10, 30));
        assertThat(types(b.getId())).containsExactly("BookingConfirmed");
        assertThat(slots()).doesNotContain(at(TUESDAY, 9, 30), at(TUESDAY, 9, 45), at(TUESDAY, 10, 0), at(TUESDAY, 10, 15));
    }

    @Test
    void thirtyPeopleRaceForOneSlotAndExactlyOneGetsIt() throws Exception {
        int people = 30;
        List<CurrentUser> customers = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            customers.add(clinic.customer(accounts));
        }
        ExecutorService pool = Executors.newFixedThreadPool(people);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (CurrentUser c : customers) {
            results.add(pool.submit(() -> {
                go.await();
                try {
                    bookings.hold(c, clinic.doctor.getId(), clinic.consult.getId(), at(TUESDAY, 11, 0));
                    return true;
                } catch (RuleViolation taken) {
                    return false;
                }
            }));
        }
        go.countDown();
        int won = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                won++;
            }
        }
        pool.shutdown();
        assertThat(won).isEqualTo(1);
    }

    @Test
    void unconfirmedHoldExpiresAndFreesTheSlot() {
        CurrentUser slow = clinic.customer(accounts);
        Booking held = bookings.hold(slow, clinic.doctor.getId(), clinic.consult.getId(), at(TUESDAY, 9, 0));
        assertThat(slots()).doesNotContain(at(TUESDAY, 9, 0));

        clock.advance(Duration.ofMinutes(11));
        assertThat(bookings.expireHolds()).isGreaterThanOrEqualTo(1);

        assertThat(slots()).contains(at(TUESDAY, 9, 0));
        assertThatThrownBy(() -> bookings.confirm(slow, held.getId())).hasMessageContaining("expired");
    }

    @Test
    void customerCannotCancelInsideTheCutoffButStaffCan() {
        CurrentUser c = clinic.customer(accounts);
        Booking b = book(c, at(TUESDAY, 12, 0));
        clock.set(at(TUESDAY, 10, 30)); // 90 minutes before, cut-off is 2 hours

        assertThatThrownBy(() -> bookings.cancel(c, b.getId(), "running late"))
                .isInstanceOf(RuleViolation.class).hasMessageContaining("within 2 hours");
        assertThat(bookings.cancel(clinic.admin, b.getId(), "patient called").getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancellingFreesTheSlotAndOffersItToTheWaitlist() {
        CurrentUser first = clinic.customer(accounts);
        CurrentUser waiting = clinic.customer(accounts);
        Booking b = book(first, at(TUESDAY, 9, 0));
        var entry = waitlist.join(waiting, clinic.doctor.getId(), clinic.consult.getId(), TUESDAY);

        bookings.cancel(first, b.getId(), "travelling");

        assertThat(slots()).contains(at(TUESDAY, 9, 0));
        assertThat(types(b.getId())).containsExactly("BookingConfirmed", "BookingCancelled");
        assertThat(types(entry.getId())).containsExactly("WaitlistSlotOpened");
    }

    @Test
    void rescheduleToATakenTimeFailsAndKeepsTheOriginal() {
        CurrentUser a = clinic.customer(accounts);
        CurrentUser b = clinic.customer(accounts);
        Booking mine = book(a, at(TUESDAY, 9, 0));
        book(b, at(TUESDAY, 10, 0));

        assertThatThrownBy(() -> bookings.reschedule(a, mine.getId(), at(TUESDAY, 10, 0))).isInstanceOf(RuleViolation.class);
        assertThat(slots()).doesNotContain(at(TUESDAY, 9, 0)); // still mine

        Booking moved = bookings.reschedule(a, mine.getId(), at(TUESDAY, 11, 0));
        assertThat(moved.getStartAt()).isEqualTo(at(TUESDAY, 11, 0));
        assertThat(slots()).contains(at(TUESDAY, 9, 0)).doesNotContain(at(TUESDAY, 11, 0));
    }

    @Test
    void remindersGoOutOnceForBookingsInTheNext24Hours() {
        Booking soon = book(clinic.customer(accounts), at(TUESDAY, 9, 0));
        clock.set(at(TUESDAY, 9, 0).minus(Duration.ofHours(20)));

        assertThat(bookings.sendReminders()).isGreaterThanOrEqualTo(1);
        assertThat(types(soon.getId())).contains("ReminderDue");
        long before = types(soon.getId()).stream().filter("ReminderDue"::equals).count();
        bookings.sendReminders();
        assertThat(types(soon.getId()).stream().filter("ReminderDue"::equals).count()).isEqualTo(before);
    }

    @Test
    void timeOffHidesSlots() {
        schedule.addTimeOff(clinic.tenant.getId(), clinic.doctor.getId(), at(TUESDAY, 9, 0), at(TUESDAY, 13, 0), "conference");
        assertThat(slots()).isEmpty();
    }

    @Test
    void anotherBusinessCannotSeeTheBooking() {
        Booking b = book(clinic.customer(accounts), at(TUESDAY, 12, 0));
        Fixture otherClinic = new Fixture(accounts, catalog, schedule);

        assertThatThrownBy(() -> bookings.visible(otherClinic.admin, b.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void customerCannotSeeSomeoneElsesBooking() {
        Booking b = book(clinic.customer(accounts), at(TUESDAY, 12, 15));
        assertThatThrownBy(() -> bookings.visible(clinic.customer(accounts), b.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void attendanceOnlyAfterTheStart() {
        Booking b = book(clinic.customer(accounts), at(TUESDAY, 9, 0));
        assertThatThrownBy(() -> bookings.recordAttendance(clinic.admin, b.getId(), true)).hasMessageContaining("after it starts");

        clock.set(at(TUESDAY, 9, 45));
        assertThat(bookings.recordAttendance(clinic.admin, b.getId(), false).getStatus()).isEqualTo(BookingStatus.NO_SHOW);
    }

    private List<Instant> slots() {
        return schedule.slots(clinic.tenant.getId(), clinic.doctor.getId(), clinic.consult.getId(), TUESDAY);
    }

    private List<String> types(UUID aggregate) {
        return outbox.findByAggregateIdOrderByCreatedAtAsc(aggregate.toString()).stream().map(OutboxEvent::getEventType).toList();
    }
}
