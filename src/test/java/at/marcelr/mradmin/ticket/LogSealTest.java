package at.marcelr.mradmin.ticket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Sealed ticket logs open again with the private key - with exactly the algorithms the website viewer uses. */
class LogSealTest {

    @Test
    void sealedLogOpensWithThePrivateKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair pair = g.generateKeyPair();
        byte[] log = TicketLogs.gzip("{\"format\":\"mrtechnology-ticket-log\",\"text\":\"Grüße\"}");
        Map<String, Object> sealed = LogSeal.seal(log, Map.of("ticket", 7), "test", pair.getPublic());
        assertEquals(LogSeal.FORMAT, sealed.get("format"));
        assertEquals("test", sealed.get("kid"));
        assertEquals(7, sealed.get("ticket"));

        Base64.Decoder b64 = Base64.getDecoder();
        Cipher unwrap = Cipher.getInstance("RSA/ECB/OAEPPadding");
        unwrap.init(Cipher.DECRYPT_MODE, pair.getPrivate(),
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] aes = unwrap.doFinal(b64.decode((String) sealed.get("key")));
        Cipher data = Cipher.getInstance("AES/GCM/NoPadding");
        data.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aes, "AES"), new GCMParameterSpec(128, b64.decode((String) sealed.get("iv"))));
        assertArrayEquals(log, data.doFinal(b64.decode((String) sealed.get("data"))));
        // nothing of the content readable from outside
        assertFalse(new String(b64.decode((String) sealed.get("data")), StandardCharsets.ISO_8859_1).contains("mrtechnology-ticket-log"));
    }

    @Test
    void theJarCarriesTheMrtechnologyKey() throws Exception {
        assertEquals("2026-11", LogSeal.kid());
    }
}
