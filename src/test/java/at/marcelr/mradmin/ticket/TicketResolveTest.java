package at.marcelr.mradmin.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Solved tickets (since 1.7): the creator may open them again for tickets.reopen-days, afterwards they are closed. */
class TicketResolveTest {

    private static Ticket ticket() {
        return new Ticket(1, UUID.randomUUID(), "Steve", null, null, "bug", "Kiste weg", Instant.now(), "world", 0, 64, 0);
    }

    @Test
    void solvedTicketMayBeOpenedAgainWithinTheDays() {
        Ticket t = ticket();
        Instant at = Instant.now().minus(Duration.ofDays(10));
        t.markResolved("Admin", at);
        assertFalse(t.open());
        assertTrue(t.resolved());
        assertTrue(t.reopenable(30));
        assertEquals(at.plus(Duration.ofDays(30)), t.reopenUntil(30));
    }

    @Test
    void afterTheDaysItIsClosedForGood() {
        Ticket t = ticket();
        t.markResolved("Admin", Instant.now().minus(Duration.ofDays(31)));
        assertFalse(t.reopenable(30));
        // still marked as solved in the storage - only the time has run out
        assertTrue(t.resolved());
    }

    @Test
    void closedByTheCreatorIsClosedForGoodAtOnce() {
        Ticket t = ticket();
        t.close("Steve", Instant.now());
        assertFalse(t.resolved());
        assertFalse(t.reopenable(30));
        assertNull(t.reopenUntil(30));
    }

    @Test
    void zeroDaysMeansNoReopening() {
        Ticket t = ticket();
        t.markResolved("Admin", Instant.now());
        assertFalse(t.reopenable(0));
        assertNull(t.reopenUntil(0));
    }

    @Test
    void openingAgainClearsTheState() {
        Ticket t = ticket();
        t.markResolved("Admin", Instant.now());
        t.reopen();
        assertTrue(t.open());
        assertFalse(t.resolved());
        assertFalse(t.reopenable(30));
    }
}
