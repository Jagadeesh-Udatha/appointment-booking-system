package dev.jagadeesh.booking.schedule;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure slot maths, no database: which start times on one local date can fit an appointment of {@code blocked}
 * length (duration + buffer) inside a working window, outside time off, without touching a busy unit, and not in
 * the past. Slot starts are every {@code step} minutes from the window start. Busy time is a set of 5-minute unit
 * starts, the same units the booking table locks.
 */
public final class SlotFinder {

    public static final Duration UNIT = Duration.ofMinutes(5);

    public record Window(java.time.LocalTime start, java.time.LocalTime end) {
    }

    public record Interval(Instant start, Instant end) {
        boolean overlaps(Instant s, Instant e) {
            return start.isBefore(e) && end.isAfter(s);
        }
    }

    private SlotFinder() {
    }

    public static List<Instant> find(LocalDate date, ZoneId zone, List<Window> windows, List<Interval> timeOff,
                                     Set<Instant> busyUnits, Duration blocked, Duration step, Instant now) {
        List<Instant> out = new ArrayList<>();
        for (Window w : windows) {
            ZonedDateTime cursor = date.atTime(w.start()).atZone(zone);
            ZonedDateTime windowEnd = date.atTime(w.end()).atZone(zone);
            while (!cursor.plus(blocked).isAfter(windowEnd)) {
                Instant start = cursor.toInstant();
                Instant end = start.plus(blocked);
                if (start.isAfter(now) && timeOff.stream().noneMatch(t -> t.overlaps(start, end)) && free(start, end, busyUnits)) {
                    out.add(start);
                }
                cursor = cursor.plus(step);
            }
        }
        return out;
    }

    public static List<Instant> units(Instant start, Instant end) {
        List<Instant> units = new ArrayList<>();
        for (Instant u = start; u.isBefore(end); u = u.plus(UNIT)) {
            units.add(u);
        }
        return units;
    }

    private static boolean free(Instant start, Instant end, Set<Instant> busy) {
        return units(start, end).stream().noneMatch(busy::contains);
    }
}
