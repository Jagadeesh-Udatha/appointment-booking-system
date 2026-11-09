package dev.jagadeesh.booking.schedule;

import dev.jagadeesh.booking.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
@RequestMapping("/api")
public class ScheduleController {

    private final ScheduleService schedule;

    public ScheduleController(ScheduleService schedule) {
        this.schedule = schedule;
    }

    public record NewTimeOff(@NotNull Instant from, @NotNull Instant to, String reason) {
    }

    @PutMapping("/providers/{providerId}/working-hours")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setHours(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID providerId, @RequestBody List<ScheduleService.Hours> week) {
        CurrentUser me = CurrentUser.of(jwt);
        me.requireAdmin();
        schedule.setWeeklyHours(me.tenantId(), providerId, week);
    }

    @PostMapping("/providers/{providerId}/time-off")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeOff addTimeOff(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID providerId, @Valid @RequestBody NewTimeOff r) {
        CurrentUser me = CurrentUser.of(jwt);
        if (!me.isStaff()) {
            throw new org.springframework.security.access.AccessDeniedException("Staff only");
        }
        return schedule.addTimeOff(me.tenantId(), providerId, r.from(), r.to(), r.reason());
    }

    @GetMapping("/slots")
    public List<Instant> slots(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID providerId, @RequestParam UUID serviceId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return schedule.slots(CurrentUser.of(jwt).tenantId(), providerId, serviceId, date);
    }
}
