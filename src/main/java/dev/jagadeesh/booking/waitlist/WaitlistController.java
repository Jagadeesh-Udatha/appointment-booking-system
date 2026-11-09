package dev.jagadeesh.booking.waitlist;

import dev.jagadeesh.booking.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class WaitlistController {

    private final WaitlistService waitlist;

    public WaitlistController(WaitlistService waitlist) {
        this.waitlist = waitlist;
    }

    public record JoinRequest(@NotNull UUID providerId, @NotNull UUID serviceId, @NotNull @FutureOrPresent LocalDate date) {
    }

    public record Joined(UUID id, String status) {
    }

    @PostMapping("/api/waitlist")
    @ResponseStatus(HttpStatus.CREATED)
    public Joined join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinRequest r) {
        WaitlistEntry e = waitlist.join(CurrentUser.of(jwt), r.providerId(), r.serviceId(), r.date());
        return new Joined(e.getId(), e.getStatus().name());
    }
}
