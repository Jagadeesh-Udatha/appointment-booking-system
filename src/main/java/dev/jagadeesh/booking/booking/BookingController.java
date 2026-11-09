package dev.jagadeesh.booking.booking;

import dev.jagadeesh.booking.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;
    private final BookingRepository bookings;

    public BookingController(BookingService service, BookingRepository bookings) {
        this.service = service;
        this.bookings = bookings;
    }

    public record HoldRequest(@NotNull UUID providerId, @NotNull UUID serviceId, @NotNull Instant startAt) {
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    public record RescheduleRequest(@NotNull Instant startAt) {
    }

    public record AttendanceRequest(boolean attended) {
    }

    public record BookingView(UUID id, UUID providerId, UUID serviceId, Instant startAt, Instant endAt, String status,
                              Instant holdExpiresAt, String statusReason) {
        static BookingView of(Booking b) {
            return new BookingView(b.getId(), b.getProviderId(), b.getServiceId(), b.getStartAt(), b.getEndAt(), b.getStatus().name(),
                    b.getHoldExpiresAt(), b.getStatusReason());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView hold(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HoldRequest r) {
        return BookingView.of(service.hold(CurrentUser.of(jwt), r.providerId(), r.serviceId(), r.startAt()));
    }

    @PostMapping("/{id}/confirm")
    public BookingView confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return BookingView.of(service.confirm(CurrentUser.of(jwt), id));
    }

    @PostMapping("/{id}/cancel")
    public BookingView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest r) {
        return BookingView.of(service.cancel(CurrentUser.of(jwt), id, r == null ? null : r.reason()));
    }

    @PostMapping("/{id}/reschedule")
    public BookingView reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RescheduleRequest r) {
        return BookingView.of(service.reschedule(CurrentUser.of(jwt), id, r.startAt()));
    }

    @PostMapping("/{id}/attendance")
    public BookingView attendance(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody AttendanceRequest r) {
        return BookingView.of(service.recordAttendance(CurrentUser.of(jwt), id, r.attended()));
    }

    @GetMapping("/{id}")
    public BookingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return BookingView.of(service.visible(CurrentUser.of(jwt), id));
    }

    @GetMapping("/mine")
    public List<BookingView> mine(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser me = CurrentUser.of(jwt);
        return bookings.findByTenantIdAndCustomerIdOrderByStartAtDesc(me.tenantId(), me.userId()).stream().map(BookingView::of).toList();
    }

    /** Front-desk day sheet for one provider. */
    @GetMapping
    public List<BookingView> day(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID providerId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.dayView(CurrentUser.of(jwt), providerId, date).stream().map(BookingView::of).toList();
    }
}
