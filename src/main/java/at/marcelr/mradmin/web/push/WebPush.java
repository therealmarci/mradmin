package at.marcelr.mradmin.web.push;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push without a library: the message is encrypted for the browser (RFC 8291, "aes128gcm": ECDH P-256 + HKDF +
 * AES-128-GCM) and the request is signed with the server's VAPID key (RFC 8292, ES256 JWT). The push service
 * (Google, Mozilla, Apple) only forwards the encrypted blob - it cannot read it.
 */
public final class WebPush {

    /** Result of one push: delivered, the subscription is gone (delete it), or another error. */
    public enum Outcome { SENT, GONE, FAILED }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;

    private final ECPrivateKey vapidPrivate;
    private final ECPublicKey vapidPublic;
    private final String subject;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** @param subject contact for the push services ("mailto:..." or "https://...") */
    public WebPush(KeyPair vapid, String subject) {
        this.vapidPrivate = (ECPrivateKey) vapid.getPrivate();
        this.vapidPublic = (ECPublicKey) vapid.getPublic();
        this.subject = subject;
    }

    // ---------------------------------------------------------------- keys

    public static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** The key pair from its stored form (private: PKCS#8, public: raw point, both base64url). */
    public static KeyPair load(String privatePkcs8, String publicRaw) throws GeneralSecurityException {
        KeyFactory factory = KeyFactory.getInstance("EC");
        ECPrivateKey priv = (ECPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(B64D.decode(privatePkcs8)));
        return new KeyPair(publicKey(B64D.decode(publicRaw), priv.getParams()), priv);
    }

    public static String privateText(KeyPair pair) {
        return B64.encodeToString(pair.getPrivate().getEncoded());
    }

    /** The public key as the browser wants it (applicationServerKey): the raw 65-byte point, base64url. */
    public static String publicText(KeyPair pair) {
        return B64.encodeToString(raw((ECPublicKey) pair.getPublic()));
    }

    public String publicKey() {
        return B64.encodeToString(raw(vapidPublic));
    }

    // ---------------------------------------------------------------- sending

    /**
     * Sends one message (UTF-8 JSON, a few KB at most) to one subscription. Asynchronous.
     * @param p256dh the browser's public key (base64url), @param auth its auth secret (base64url)
     */
    public CompletableFuture<Outcome> send(String endpoint, String p256dh, String auth, String payload, int ttlSeconds,
                                           boolean urgent) {
        try {
            byte[] body = encrypt(payload.getBytes(StandardCharsets.UTF_8), B64D.decode(p256dh), B64D.decode(auth));
            URI uri = URI.create(endpoint);
            String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(ttlSeconds))
                    .header("Urgency", urgent ? "high" : "normal")
                    .header("Authorization", "vapid t=" + jwt(audience) + ", k=" + publicKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).handle((response, error) -> {
                if (error != null) {
                    return Outcome.FAILED;
                }
                int code = response.statusCode();
                if (code == 404 || code == 410) {
                    return Outcome.GONE;
                }
                return code >= 200 && code < 300 ? Outcome.SENT : Outcome.FAILED;
            });
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Outcome.FAILED);
        }
    }

    /** RFC 8291 "aes128gcm": one record, header = salt | record size | key id length | server's public key. */
    byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, salt, newKeyPair());
    }

    /** With a given salt and server key pair - for the test against the example in RFC 8291. */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, byte[] salt, KeyPair ephemeral)
            throws GeneralSecurityException {
        byte[] asPublic = raw((ECPublicKey) ephemeral.getPublic());
        ECPublicKey ua = publicKey(uaPublic, ((ECPrivateKey) ephemeral.getPrivate()).getParams());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(ua, true);
        byte[] ecdhSecret = agreement.generateSecret();

        // IKM = HKDF(auth secret, ECDH secret, "WebPush: info" 0x00 | ua_public | as_public, 32)
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // The last (and only) record ends with the delimiter 0x02, no padding.
        byte[] padded = Arrays.copyOf(plaintext, plaintext.length + 1);
        padded[plaintext.length] = 0x02;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        ByteBuffer out = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length);
        out.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(ciphertext);
        return out.array();
    }

    /** VAPID: a short-lived ES256 JWT for the push service's origin. */
    String jwt(String audience) throws GeneralSecurityException {
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        long exp = System.currentTimeMillis() / 1000 + 12 * 3600;
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\""
                + subject.replace("\"", "") + "\"}").getBytes(StandardCharsets.UTF_8));
        String input = header + "." + claims;
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(vapidPrivate);
        signer.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + B64.encodeToString(signer.sign());
    }

    // ---------------------------------------------------------------- helpers

    /** HKDF-SHA256 (RFC 5869) for at most 32 bytes: one expand round is enough. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    /** Uncompressed point: 0x04 | x (32) | y (32). */
    static byte[] raw(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copy32(key.getW().getAffineX(), out, 1);
        copy32(key.getW().getAffineY(), out, 33);
        return out;
    }

    private static void copy32(BigInteger value, byte[] into, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - 32);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, into, offset + 32 - length, length);
    }

    static ECPublicKey publicKey(byte[] raw, ECParameterSpec params) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 4) {
            throw new GeneralSecurityException("not an uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, params));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}
