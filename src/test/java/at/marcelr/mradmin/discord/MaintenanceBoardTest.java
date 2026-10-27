package at.marcelr.mradmin.discord;

import at.marcelr.mradmin.discord.MaintenanceBoard.Change;
import at.marcelr.mradmin.discord.MaintenanceBoard.Entry;
import at.marcelr.mradmin.discord.MaintenanceBoard.Status;
import at.marcelr.mradmin.maintenance.Window;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceBoardTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final MaintenanceBoard.Rules RULES =
            new MaintenanceBoard.Rules(Duration.ofHours(24), Duration.ZERO, Duration.ofSeconds(60));

    private final MaintenanceBoard board = new MaintenanceBoard();

    private static Instant at(String time) {
        return Instant.parse("2026-10-27T" + time + ":00Z");
    }

    private static Window kuma(int id, String start, String end) {
        return new Window(Window.KUMA, id, "Update", start == null ? null : at(start), end == null ? null : at(end), false);
    }

    private static Window manual(String start, String end) {
        return new Window(Window.MANUAL, 0, "", at(start), end == null ? null : at(end), false);
    }

    private List<Change> update(Instant now, Window... windows) {
        return board.update(List.of(windows), now, false, RULES);
    }

    private static Change only(List<Change> changes) {
        assertEquals(1, changes.size(), "changes: " + changes);
        return changes.getFirst();
    }

    // ------------------------------------------------------------------ first post

    @Test
    void farAwayIsNotPostedYet() {
        assertTrue(board.update(List.of(new Window(Window.KUMA, 1, "", NOW.plus(Duration.ofDays(3)),
                NOW.plus(Duration.ofDays(3)).plusSeconds(3600), false)), NOW, false, RULES).isEmpty());
    }

    @Test
    void scheduledIsPostedOnceWithPing() {
        Window w = kuma(1, "14:00", "16:00");
        Change change = only(update(NOW, w));
        assertTrue(change.post());
        assertTrue(change.ping());
        assertEquals(Status.SCHEDULED, change.entry().status);
        assertTrue(update(NOW.plusSeconds(1), w).isEmpty());
    }

    @Test
    void runningWithoutEarlierPostIsPostedAsRunning() {
        Change change = only(update(NOW, kuma(1, "11:30", "13:00")));
        assertTrue(change.post());
        assertEquals(Status.RUNNING, change.entry().status);
        assertEquals(at("11:30"), change.entry().startedAt);
    }

    @Test
    void shorterThanMinLengthIsNotPosted() {
        var rules = new MaintenanceBoard.Rules(Duration.ofHours(24), Duration.ofHours(1), Duration.ZERO);
        assertTrue(board.update(List.of(kuma(1, "14:00", "14:30")), NOW, false, rules).isEmpty());
    }

    // ------------------------------------------------------------------ one message through all states

    @Test
    void startEditsTheSameMessage() {
        Window w = kuma(1, "14:00", "16:00");
        update(NOW, w);
        Change change = only(update(at("14:00"), w));
        assertFalse(change.post());
        assertFalse(change.ping());
        assertEquals(Status.RUNNING, change.entry().status);
        assertEquals(at("14:00"), change.entry().startedAt);
    }

    @Test
    void endOnTimeEditsTheMessageWithThePlannedEnd() {
        Window w = kuma(1, "14:00", "16:00");
        update(NOW, w);
        update(at("14:00"), w);
        Change change = only(update(at("16:00"), w));
        assertFalse(change.post());
        assertEquals(Status.ENDED, change.entry().status);
        assertEquals(at("16:00"), change.entry().endedAt);
        assertFalse(change.entry().endedEarly());
        assertTrue(board.entries().isEmpty());
    }

    @Test
    void earlyEndShowsTheActualEnd() {
        Window w = kuma(1, "14:00", "18:00");
        update(NOW, w);
        update(at("14:00"), w);
        // "/mradmin maintenance off" or ended in Uptime Kuma: the window is gone.
        Change change = only(update(at("15:42")));
        assertEquals(Status.ENDED, change.entry().status);
        assertEquals(at("15:42"), change.entry().endedAt);
        assertTrue(change.entry().endedEarly());
    }

    @Test
    void openEndEndsWhenStopped() {
        Window w = manual("12:00", null);
        update(NOW, w);
        Change change = only(update(at("13:10")));
        assertEquals(Status.ENDED, change.entry().status);
        assertEquals(at("13:10"), change.entry().endedAt);
        assertFalse(change.entry().endedEarly());
    }

    @Test
    void goneBeforeItsStartIsCancelled() {
        update(NOW, kuma(1, "14:00", "16:00"));
        Change change = only(update(at("13:00")));
        assertFalse(change.post());
        assertEquals(Status.CANCELLED, change.entry().status);
        assertTrue(board.entries().isEmpty());
    }

    @Test
    void earlierStartIsTakenOver() {
        update(NOW, kuma(1, "14:00", "16:00"));
        Change change = only(update(at("13:00"), kuma(1, "13:00", "16:00")));
        assertEquals(Status.RUNNING, change.entry().status);
        assertEquals(at("13:00"), change.entry().startedAt);
        assertEquals(at("13:00"), change.entry().start);
    }

    // ------------------------------------------------------------------ changed times

    @Test
    void newTimesAreEditedInAfterTheDebounce() {
        update(NOW, kuma(1, "14:00", "16:00"));
        Window moved = kuma(1, "15:00", "17:00");
        assertTrue(update(NOW.plusSeconds(10), moved).isEmpty());
        assertTrue(update(NOW.plusSeconds(69), moved).isEmpty());
        Change change = only(update(NOW.plusSeconds(70), moved));
        assertFalse(change.post());
        assertTrue(change.entry().changed);
        assertEquals(at("15:00"), change.entry().start);
        assertEquals(at("17:00"), change.entry().end);
    }

    @Test
    void extendingWhileRunningKeepsTheActualBegin() {
        update(NOW, new Window(Window.KUMA, 1, "", null, at("13:00"), true));
        Window longer = new Window(Window.KUMA, 1, "", null, at("14:00"), true);
        update(NOW.plusSeconds(1), longer);
        Change change = only(update(NOW.plusSeconds(61), longer));
        assertEquals(Status.RUNNING, change.entry().status);
        assertEquals(NOW, change.entry().startedAt);
        assertEquals(at("14:00"), change.entry().end);
    }

    // ------------------------------------------------------------------ Uptime Kuma and restarts

    @Test
    void staleKumaListChangesNothing() {
        Window w = kuma(1, "11:00", "13:00");
        update(NOW, w);
        // Reconnect: connected, but the list has not arrived yet.
        assertTrue(board.update(List.of(), NOW.plusSeconds(5), true, RULES).isEmpty());
        assertTrue(update(NOW.plusSeconds(6), w).isEmpty());
        assertEquals(Status.RUNNING, board.entries().iterator().next().status);
    }

    @Test
    void staleKumaDoesNotHoldBackManualMaintenance() {
        Change change = only(board.update(List.of(manual("12:00", "13:00")), NOW, true, RULES));
        assertEquals(Window.MANUAL + ":0", change.entry().key);
    }

    @Test
    void scheduledThatPassedWhileOfflineCountsAsHeld() {
        Entry e = new Entry(Window.MANUAL + ":0");
        e.status = Status.SCHEDULED;
        e.start = at("08:00");
        e.end = at("10:00");
        board.restore(e);
        Change change = only(update(NOW));
        assertEquals(Status.ENDED, change.entry().status);
        assertEquals(at("08:00"), change.entry().startedAt);
        assertEquals(at("10:00"), change.entry().endedAt);
        assertFalse(change.entry().endedEarly());
    }

    @Test
    void nextOccurrenceOfARecurringMaintenanceGetsANewMessage() {
        update(NOW, kuma(1, "11:00", "13:00"));
        List<Change> changes = update(at("13:00"), kuma(1, "20:00", "22:00"));
        assertEquals(2, changes.size());
        assertEquals(Status.ENDED, changes.get(0).entry().status);
        assertFalse(changes.get(0).post());
        assertEquals(Status.SCHEDULED, changes.get(1).entry().status);
        assertTrue(changes.get(1).post());
    }

    @Test
    void severalMaintenancesAreFollowedSeparately() {
        List<Change> changes = update(NOW, kuma(1, "11:00", "13:00"), kuma(2, "15:00", "16:00"));
        assertEquals(2, changes.size());
        Change ended = only(update(at("13:00"), kuma(2, "15:00", "16:00")));
        assertEquals(Window.KUMA + ":1", ended.entry().key);
        assertEquals(1, board.entries().size());
    }
}
