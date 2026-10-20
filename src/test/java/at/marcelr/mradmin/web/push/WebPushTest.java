package at.marcelr.mradmin.web.push;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushTest {

    private static final Base64.Decoder D = Base64.getUrlDecoder();
    private static final Base64.Encoder E = Base64.getUrlEncoder().withoutPadding();

    /** The example of RFC 8291, section 5: same keys and salt must give exactly the same message. */
    @Test
    void encryptsLikeTheRfcExample() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        byte[] asPrivate = D.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] asPublic = D.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] uaPublic = D.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = D.decode("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = D.decode("DGv6ra1nlYgDCS1FRnbzlw");

        var params = ((ECPrivateKey) WebPush.newKeyPair().getPrivate()).getParams();
        ECPrivateKey priv = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, asPrivate), params));
        KeyPair server = new KeyPair(WebPush.publicKey(asPublic, params), priv);

        byte[] body = WebPush.encrypt(plaintext, uaPublic, auth, salt, server);
        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6Tlz"
                + "AC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
                E.encodeToString(body));
    }

    @Test
    void signsAValidVapidToken() throws Exception {
        KeyPair vapid = WebPush.newKeyPair();
        WebPush push = new WebPush(vapid, "mailto:test@example.com");
        String jwt = push.jwt("https://push.example.net");
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        assertTrue(new String(D.decode(parts[1]), StandardCharsets.UTF_8).contains("\"aud\":\"https://push.example.net\""));
        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify((ECPublicKey) vapid.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verify.verify(D.decode(parts[2])));
        assertEquals(65, D.decode(push.publicKey()).length);
    }

    @Test
    void keysSurviveStoring() throws Exception {
        KeyPair pair = WebPush.newKeyPair();
        KeyPair loaded = WebPush.load(WebPush.privateText(pair), WebPush.publicText(pair));
        assertEquals(WebPush.publicText(pair), WebPush.publicText(loaded));
    }
}
