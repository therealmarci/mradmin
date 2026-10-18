package at.marcelr.mradmin.maintenance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KumaParserTest {

    private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");
    private static final Instant NOW = Instant.parse("2026-10-18T16:00:00Z"); // 18:00 in Vienna

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void scheduledSingleWithWallClockTimesInItsTimezone() {
        Window w = KumaParser.parse(json("""
                {"id": 3, "title": "Update", "active": true, "status": "scheduled", "strategy": "single",
                 "timezone": "Europe/Vienna", "timezoneOffset": "+02:00",
                 "timeslotList": [{"startDate": "2026-10-18 20:00:00", "endDate": "2026-10-18 22:00:00"}]}
                """), NOW, ZoneId.of("UTC"));
        assertNotNull(w);
        assertEquals(3, w.id());
        assertEquals("Update", w.title());
        assertEquals(Instant.parse("2026-10-18T18:00:00Z"), w.start());
        assertEquals(Instant.parse("2026-10-18T20:00:00Z"), w.end());
        assertFalse(w.running());
        assertTrue(w.upcomingAt(NOW));
    }

    @Test
    void recurringWithIsoTimesPicksTheNextSlot() {
        Window w = KumaParser.parse(json("""
                {"id": 5, "title": "Nightly", "active": true, "status": "scheduled", "strategy": "recurring-interval",
                 "timeslotList": [{"startDate": "2026-10-19T02:00:00.000Z", "endDate": "2026-10-19T02:30:00.000Z"}]}
                """), NOW, VIENNA);
        assertNotNull(w);
        assertEquals(Instant.parse("2026-10-19T02:00:00Z"), w.start());
    }

    @Test
    void runningUsesTheCurrentSlotForTheEnd() {
        Window w = KumaParser.parse(json("""
                {"id": 7, "active": true, "status": "under-maintenance",
                 "timeslotList": [{"startDate": "2026-10-18T15:30:00Z", "endDate": "2026-10-18T17:00:00Z"}]}
                """), NOW, VIENNA);
        assertNotNull(w);
        assertTrue(w.running());
        assertTrue(w.activeAt(NOW));
        assertEquals(Instant.parse("2026-10-18T17:00:00Z"), w.end());
    }

    @Test
    void manualRunningHasNoEnd() {
        Window w = KumaParser.parse(json("""
                {"id": 9, "active": true, "status": "under-maintenance", "strategy": "manual", "timeslotList": []}
                """), NOW, VIENNA);
        assertNotNull(w);
        assertNull(w.start());
        assertNull(w.end());
        assertTrue(w.activeAt(NOW));
    }

    @Test
    void inactiveEndedAndPausedAreIgnored() {
        assertNull(KumaParser.parse(json("{\"id\": 1, \"status\": \"inactive\"}"), NOW, VIENNA));
        assertNull(KumaParser.parse(json("{\"id\": 1, \"status\": \"ended\"}"), NOW, VIENNA));
        assertNull(KumaParser.parse(json("""
                {"id": 1, "active": false, "status": "scheduled",
                 "timeslotList": [{"startDate": "2026-10-19T02:00:00Z", "endDate": "2026-10-19T03:00:00Z"}]}
                """), NOW, VIENNA));
        // Kuma 1.23 sends active as 0/1
        assertNull(KumaParser.parse(json("""
                {"id": 1, "active": 0, "status": "scheduled",
                 "timeslotList": [{"startDate": "2026-10-19T02:00:00Z", "endDate": "2026-10-19T03:00:00Z"}]}
                """), NOW, VIENNA));
    }

    @Test
    void scheduledSlotThatAlreadyBeganCountsAsActive() {
        // Kuma flips the status a moment after the start - the window must already count as running.
        Window w = KumaParser.parse(json("""
                {"id": 2, "active": 1, "status": "scheduled",
                 "timeslotList": [{"startDate": "2026-10-18T15:59:58Z", "endDate": "2026-10-18T17:00:00Z"}]}
                """), NOW, VIENNA);
        assertNotNull(w);
        assertTrue(w.activeAt(NOW));
    }

    @Test
    void offsetIsUsedWhenTimezoneIsSameAsServer() {
        Window w = KumaParser.parse(json("""
                {"id": 4, "status": "scheduled", "timezone": "SAME_AS_SERVER", "timezoneOffset": "+01:00",
                 "timeslotList": [{"startDate": "2026-10-18 20:00", "endDate": "2026-10-18 21:00"}]}
                """), NOW, VIENNA);
        assertNotNull(w);
        assertEquals(Instant.parse("2026-10-18T19:00:00Z"), w.start());
    }
}
