package at.marcelr.mradmin.web.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Time-based one-time codes (RFC 6238) as shown by Google Authenticator, Aegis, 1Password ...: 6 digits, a new code
 * every 30 seconds. One step before and after counts too (clocks are never exact).
 */
public final class Totp {

    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    private Totp() {
    }

    /** A new secret (160 bits, Base32) for an authenticator app. */
    public static String newSecret() {
        return Secrets.base32(Secrets.random(20));
    }

    /** The otpauth:// link the authenticator app reads from the QR code. */
    public static String uri(String issuer, String account, String secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * Checks a code. @return the time step it belongs to (to refuse using the same code twice), or -1 if wrong
     */
    public static long verify(String secret, String code, long nowMillis) {
        if (code == null) {
            return -1;
        }
        String digits = code.replace(" ", "");
        if (!digits.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        long step = nowMillis / 1000 / STEP_SECONDS;
        byte[] key = Secrets.fromBase32(secret);
        for (long s = step - 1; s <= step + 1; s++) {
            if (Secrets.equal(code(key, s), digits)) {
                return s;
            }
        }
        return -1;
    }

    static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
