package at.marcelr.mradmin.ticket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Seals a ticket log for MRtechnology (since 1.7): only the holder of MRtechnology's private key can open it - not
 * even the server that wrote it. A fresh AES-256-GCM key encrypts the (gzipped) log, RSA-OAEP with SHA-256 wraps that
 * key with the public key in {@code seal/mrtechnology-keys.json}. The result may lie in a public GitHub issue; the
 * viewer on the MRtechnology website opens it in the browser (Web Crypto, same algorithms).
 */
public final class LogSeal {

    public static final String FORMAT = "mrtechnology-ticket-log-sealed";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    private static volatile Key key;

    private record Key(String kid, PublicKey publicKey) {
    }

    private LogSeal() {
    }

    /** The current key (the first in the list - a new key is put on top, older ones stay for reference). */
    private static Key key() throws GeneralSecurityException, IOException {
        Key k = key;
        if (k != null) {
            return k;
        }
        try (InputStream in = LogSeal.class.getClassLoader().getResourceAsStream("seal/mrtechnology-keys.json")) {
            if (in == null) {
                throw new IOException("seal/mrtechnology-keys.json is missing in the JAR");
            }
            JsonObject first = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("keys").get(0).getAsJsonObject();
            byte[] spki = Base64.getDecoder().decode(first.get("spki").getAsString());
            k = new Key(first.get("kid").getAsString(), KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki)));
        }
        key = k;
        return k;
    }

    /**
     * @param gzipped the log as gzipped JSON (like it is stored)
     * @param meta    a few plain facts shown before opening (ticket number, time) - nothing personal
     * @return the sealed file as JSON
     */
    public static Map<String, Object> seal(byte[] gzipped, Map<String, Object> meta) throws GeneralSecurityException, IOException {
        return seal(gzipped, meta, key());
    }

    /** The current key id from the JAR (tests). */
    static String kid() throws GeneralSecurityException, IOException {
        return key().kid();
    }

    static Map<String, Object> seal(byte[] gzipped, Map<String, Object> meta, String kid, PublicKey publicKey)
            throws GeneralSecurityException {
        return seal(gzipped, meta, new Key(kid, publicKey));
    }

    private static Map<String, Object> seal(byte[] gzipped, Map<String, Object> meta, Key k) throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256, RANDOM);
        SecretKey aes = generator.generateKey();
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher data = Cipher.getInstance("AES/GCM/NoPadding");
        data.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, iv));
        byte[] encrypted = data.doFinal(gzipped);
        Cipher wrap = Cipher.getInstance("RSA/ECB/OAEPPadding");
        wrap.init(Cipher.ENCRYPT_MODE, k.publicKey(), OAEP);
        byte[] wrapped = wrap.doFinal(aes.getEncoded());
        Base64.Encoder b64 = Base64.getEncoder();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", FORMAT);
        result.put("version", 1);
        result.put("kid", k.kid());
        result.put("alg", "RSA-OAEP-256+A256GCM");
        result.putAll(meta);
        result.put("key", b64.encodeToString(wrapped));
        result.put("iv", b64.encodeToString(iv));
        result.put("data", b64.encodeToString(encrypted));
        return result;
    }
}
