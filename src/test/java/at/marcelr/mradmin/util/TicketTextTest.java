package at.marcelr.mradmin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketTextTest {

    @Test
    void cleanTrimsAndUnifiesLineBreaks() {
        assertEquals("a\nb", TicketText.clean("  a\r\nb  "));
        assertEquals("a\n\nb", TicketText.clean("a\n\n\n\n\nb"));
        assertEquals("a\nb", TicketText.clean("a   \nb"));
        assertEquals("a b", TicketText.clean("a\tb"));
        assertEquals("ab", TicketText.clean("a\u0007b"));
        assertEquals("", TicketText.clean(null));
    }

    @Test
    void limitCuts() {
        assertEquals("abc", TicketText.limit("abc", 5));
        assertEquals("ab", TicketText.limit("ab cd", 3));
    }

    @Test
    void previewIsOneShortLine() {
        assertEquals("a b c", TicketText.preview("a\nb   c", 20));
        assertEquals("abcd…", TicketText.preview("abcdefgh", 5));
        assertEquals("", TicketText.preview(null, 5));
    }
}
