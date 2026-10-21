package at.marcelr.mradmin.announce;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The script syntax of /mradmin announce. */
class AnnounceArgsTest {

    @Test
    void partsWithQuotedValues() {
        AnnounceArgs args = AnnounceArgs.parse(
                "title=\"<red><bold>Server stop\" subtitle=\"in <white>5 minutes\" sound=BLOCK_NOTE_BLOCK_BELL stay=4");
        assertEquals(List.of(), args.words());
        assertEquals("<red><bold>Server stop", args.values().get("title"));
        assertEquals("in <white>5 minutes", args.values().get("subtitle"));
        assertEquals("BLOCK_NOTE_BLOCK_BELL", args.values().get("sound"));
        assertEquals("4", args.values().get("stay"));
        assertTrue(args.hasParts());
    }

    @Test
    void presetWithNamedValues() {
        AnnounceArgs args = AnnounceArgs.parse("restart time=\"5 Minuten\" reason=Update");
        assertEquals(List.of("restart"), args.words());
        assertFalse(args.hasParts());
        assertEquals(Map.of("time", "5 Minuten", "reason", "Update"), args.placeholders());
    }

    @Test
    void oldSyntaxStillWorks() {
        AnnounceArgs args = AnnounceArgs.parse("restart 5m");
        assertEquals(List.of("restart", "5m"), args.words());
        AnnounceArgs text = AnnounceArgs.parse("Heute Abend Event | ab 20 Uhr");
        assertEquals(List.of("Heute", "Abend", "Event", "|", "ab", "20", "Uhr"), text.words());
        assertFalse(text.hasParts());
    }

    @Test
    void escapedQuotesAndEqualsInText() {
        AnnounceArgs args = AnnounceArgs.parse("chat=\"Er sagte \\\"Hallo\\\"\" a=b=c 1+1=2");
        assertEquals("Er sagte \"Hallo\"", args.values().get("chat"));
        assertEquals("b=c", args.values().get("a"));
        assertEquals(List.of("1+1=2"), args.words());
    }
}
