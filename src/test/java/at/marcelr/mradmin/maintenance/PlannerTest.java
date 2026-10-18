package at.marcelr.mradmin.maintenance;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T16:00:00Z");

    private static Window window(int id, String start, String end) {
        return new Window(Window.KUMA, id, "", start == null ? null : Instant.parse(start),
                end == null ? null : Instant.parse(end), false);
    }

    @Test
    void nothingKnown() {
        assertNull(Planner.choose(List.of(), NOW));
    }

    @Test
    void nextUpcomingWins() {
        Window later = window(1, "2026-10-19T10:00:00Z", "2026-10-19T11:00:00Z");
        Window sooner = window(2, "2026-10-18T18:00:00Z", "2026-10-18T19:00:00Z");
        assertEquals(sooner, Planner.choose(List.of(later, sooner), NOW));
    }

    @Test
    void activeBeatsUpcoming() {
        Window active = window(1, "2026-10-18T15:00:00Z", "2026-10-18T17:00:00Z");
        Window upcoming = window(2, "2026-10-18T16:05:00Z", "2026-10-18T19:00:00Z");
        assertEquals(1, Planner.choose(List.of(upcoming, active), NOW).id());
    }

    @Test
    void overlappingActiveWindowsAreMerged() {
        Window a = window(1, "2026-10-18T15:00:00Z", "2026-10-18T17:00:00Z");
        Window b = window(2, "2026-10-18T15:30:00Z", "2026-10-18T18:00:00Z");
        Window merged = Planner.choose(List.of(a, b), NOW);
        assertEquals(Instant.parse("2026-10-18T15:00:00Z"), merged.start());
        assertEquals(Instant.parse("2026-10-18T18:00:00Z"), merged.end());
        assertTrue(merged.activeAt(NOW));
    }

    @Test
    void openEndedActiveWindowKeepsTheMergeOpen() {
        Window a = window(1, "2026-10-18T15:00:00Z", "2026-10-18T17:00:00Z");
        Window b = window(2, null, null);
        assertNull(Planner.choose(List.of(a, b), NOW).end());
    }

    @Test
    void endedWindowIsIgnored() {
        Window over = window(1, "2026-10-18T14:00:00Z", "2026-10-18T15:00:00Z");
        assertNull(Planner.choose(List.of(over), NOW));
    }
}
