package dev.jagadeesh.booking;

import dev.jagadeesh.booking.schedule.SlotFinder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlotFinderTest {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final LocalDate DAY = LocalDate.of(2030, 1, 8);
    static final List<SlotFinder.Window> NINE_TO_ELEVEN = List.of(new SlotFinder.Window(LocalTime.of(9, 0), LocalTime.of(11, 0)));
    static final Duration FORTY = Duration.ofMinutes(40); // 30 min appointment + 10 min buffer
    static final Duration STEP = Duration.ofMinutes(15);
    static final Instant LONG_AGO = Instant.parse("2000-01-01T00:00:00Z");

    static Instant at(int h, int m) {
        return ZonedDateTime.of(DAY, LocalTime.of(h, m), IST).toInstant();
    }

    @Test
    void slotsMustEndInsideTheWindow() {
        assertThat(SlotFinder.find(DAY, IST, NINE_TO_ELEVEN, List.of(), Set.of(), FORTY, STEP, LONG_AGO))
                .containsExactly(at(9, 0), at(9, 15), at(9, 30), at(9, 45), at(10, 0), at(10, 15));
    }

    @Test
    void aBusyUnitRemovesEverySlotThatWouldCoverIt() {
        assertThat(SlotFinder.find(DAY, IST, NINE_TO_ELEVEN, List.of(), Set.of(at(9, 30)), FORTY, STEP, LONG_AGO))
                .containsExactly(at(9, 45), at(10, 0), at(10, 15));
    }

    @Test
    void timeOffBlocksOverlappingSlots() {
        var lunch = new SlotFinder.Interval(at(10, 0), at(10, 30));
        assertThat(SlotFinder.find(DAY, IST, NINE_TO_ELEVEN, List.of(lunch), Set.of(), FORTY, STEP, LONG_AGO))
                .containsExactly(at(9, 0), at(9, 15));
    }

    @Test
    void pastSlotsAreHidden() {
        assertThat(SlotFinder.find(DAY, IST, NINE_TO_ELEVEN, List.of(), Set.of(), FORTY, STEP, at(9, 50)))
                .containsExactly(at(10, 0), at(10, 15));
    }

    @Test
    void splitShiftsAreBothUsed() {
        var shifts = List.of(new SlotFinder.Window(LocalTime.of(9, 0), LocalTime.of(9, 40)), new SlotFinder.Window(LocalTime.of(17, 0), LocalTime.of(17, 40)));
        assertThat(SlotFinder.find(DAY, IST, shifts, List.of(), Set.of(), FORTY, STEP, LONG_AGO)).containsExactly(at(9, 0), at(17, 0));
    }

    @Test
    void localTimeIsTheBusinessTimeZone() {
        assertThat(SlotFinder.find(DAY, IST, NINE_TO_ELEVEN, List.of(), Set.of(), FORTY, STEP, LONG_AGO).get(0))
                .isEqualTo(Instant.parse("2030-01-08T03:30:00Z"));
    }

    @Test
    void unitsCoverTheBlockedTimeIn5MinuteSteps() {
        assertThat(SlotFinder.units(at(9, 0), at(9, 40))).hasSize(8).first().isEqualTo(at(9, 0));
    }
}
