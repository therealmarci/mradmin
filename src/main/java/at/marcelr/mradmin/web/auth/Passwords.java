package at.marcelr.mradmin.web.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Password hashes with PBKDF2-HMAC-SHA256 (built into Java, OWASP recommendation: 600,000 rounds). Stored as
 * {@code pbkdf2$<rounds>$<salt>$<hash>}, so the rounds can be raised later without breaking old hashes.
 * Hashing takes a noticeable moment on purpose - never call it on the server thread.
 */
public final class Passwords {

    public static final int MIN_LENGTH = 10;
    private static final int ROUNDS = 600_000;
    private static final int KEY_BITS = 256;

    private Passwords() {
    }

    public static String hash(String password) {
        byte[] salt = Secrets.random(16);
        return "pbkdf2$" + ROUNDS + "$" + b64(salt) + "$" + b64(derive(password, salt, ROUNDS));
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, Integer.parseInt(parts[1])));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().withoutPadding().encodeToString(data);
    }
}
