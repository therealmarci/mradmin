package at.marcelr.mradmin.starter;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevSealTest {

    private static final byte[] PLAIN = "a plugin jar".repeat(500).getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTrip() throws Exception {
        assertArrayEquals(PLAIN, DevSeal.open(DevSeal.seal(PLAIN, "token-123"), " token-123 "));
    }

    @Test
    void wrongTokenOrChangedFileIsRejected() throws Exception {
        byte[] sealed = DevSeal.seal(PLAIN, "token-123");
        assertThrows(DevSeal.BadTokenException.class, () -> DevSeal.open(sealed, "token-124"));
        assertThrows(DevSeal.BadTokenException.class, () -> DevSeal.open(sealed, ""));
        for (int at : new int[]{3, 10, 30, 200, sealed.length - 1}) {
            byte[] changed = sealed.clone();
            changed[at] ^= 1;
            assertThrows(DevSeal.BadTokenException.class, () -> DevSeal.open(changed, "token-123"), "byte " + at);
        }
        assertThrows(DevSeal.BadTokenException.class, () -> DevSeal.open(PLAIN, "token-123"));
    }
}
