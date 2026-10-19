package at.marcelr.mradmin.web.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCryptoTest {

    /** RFC 6238, appendix B (SHA-1 secret "12345678901234567890"), last 6 of the 8 digits. */
    @Test
    void totpMatchesRfcVectors() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertEquals("287082", Totp.code(key, 59 / 30));
        assertEquals("081804", Totp.code(key, 1111111109L / 30));
        assertEquals("050471", Totp.code(key, 1111111111L / 30));
        assertEquals("005924", Totp.code(key, 1234567890L / 30));
    }

    @Test
    void totpAcceptsNeighbourStepsOnly() {
        String secret = Secrets.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        long now = 1111111109L * 1000;
        assertEquals(1111111109L / 30, Totp.verify(secret, "081804", now));
        assertEquals(-1, Totp.verify(secret, "081804", now + 120_000));
        assertEquals(-1, Totp.verify(secret, "12345", now));
        assertEquals(-1, Totp.verify(secret, null, now));
    }

    @Test
    void base32RoundTrip() {
        byte[] data = Secrets.random(20);
        assertArrayEquals(data, Secrets.fromBase32(Secrets.base32(data)));
        assertEquals("GEZDGNBVGY3TQOJQ", Secrets.base32("1234567890".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void passwordHashVerifies() {
        String hash = Passwords.hash("correct horse battery");
        assertTrue(hash.startsWith("pbkdf2$600000$"));
        assertTrue(Passwords.verify("correct horse battery", hash));
        assertFalse(Passwords.verify("correct horse batterY", hash));
        assertFalse(Passwords.verify("x", "garbage"));
    }
}
