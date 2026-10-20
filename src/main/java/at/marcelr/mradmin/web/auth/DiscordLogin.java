package at.marcelr.mradmin.web.auth;

import at.marcelr.mradmin.MRAdmin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Sign in with Discord" (OAuth2 authorization code, scope "identify" only - ID, name, avatar; no email, no servers).
 * Three uses: sign in (only with a linked Discord account), link the Discord account to the signed-in web account,
 * and confirm a sensitive step (instead of a 2FA code). Every flow has a one-time random {@code state}, valid for
 * 10 minutes and bound to the browser session that started it.
 */
public final class DiscordLogin {

    /** A flow in progress: what it is for, and (link / confirm) which account and session started it. */
    public record Pending(String mode, UUID account, String session, String returnTo, long expires) {
    }

    /** The result: the flow and who signed in at Discord. */
    public record Identity(Pending flow, String id, String name) {
    }

    private static final String AUTHORIZE = "https://discord.com/oauth2/authorize";
    private static final String TOKEN = "https://discord.com/api/oauth2/token";
    private static final String ME = "https://discord.com/api/users/@me";
    private static final long FLOW_MILLIS = 10 * 60_000L;
    private static final int MAX_PENDING = 500;

    private final MRAdmin plugin;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public DiscordLogin(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private String clientId() {
        return plugin.getConfig().getString("web.login.discord.client-id", "").trim();
    }

    private String secret() {
        return plugin.getConfig().getString("web.login.discord.client-secret", "").trim();
    }

    /** Switched on and filled in. */
    public boolean enabled() {
        return plugin.getConfig().getBoolean("web.login.discord.enabled", false) && !clientId().isEmpty() && !secret().isEmpty();
    }

    /** The address Discord sends the browser back to - must be entered exactly like this in the Discord app. */
    public String redirectUri() {
        String url = plugin.settings().web().publicUrl();
        String base = url.isEmpty() ? "http://localhost:" + plugin.settings().web().port() : url.replaceAll("/+$", "");
        return base + "/api/discord/callback";
    }

    /** Starts a flow. @return the Discord address to send the browser to */
    public String start(String mode, UUID account, String session, String returnTo) {
        long now = System.currentTimeMillis();
        pending.values().removeIf(p -> p.expires() < now);
        if (pending.size() > MAX_PENDING) {
            throw new IllegalStateException("too many sign-ins in progress");
        }
        String state = Secrets.token(24);
        pending.put(state, new Pending(mode, account, session, returnTo, now + FLOW_MILLIS));
        return AUTHORIZE + "?response_type=code&scope=identify&prompt=" + (mode.equals("link") ? "consent" : "none")
                + "&client_id=" + enc(clientId()) + "&redirect_uri=" + enc(redirectUri()) + "&state=" + enc(state);
    }

    /** Whether a state belongs to a running flow (the callback only passes those on). */
    public boolean known(String state) {
        Pending p = state == null ? null : pending.get(state);
        return p != null && p.expires() >= System.currentTimeMillis();
    }

    /** Finishes a flow (once): exchanges the code and asks Discord who it is. Blocking - web thread. */
    public Identity finish(String state, String code) throws Exception {
        Pending flow = state == null ? null : pending.remove(state);
        if (flow == null || flow.expires() < System.currentTimeMillis() || code == null || code.isBlank()) {
            return null;
        }
        String form = "client_id=" + enc(clientId()) + "&client_secret=" + enc(secret()) + "&grant_type=authorization_code"
                + "&code=" + enc(code) + "&redirect_uri=" + enc(redirectUri());
        HttpResponse<String> token = http.send(HttpRequest.newBuilder(URI.create(TOKEN))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        if (token.statusCode() != 200) {
            plugin.getLogger().info("Discord sign-in failed (token " + token.statusCode() + ").");
            return null;
        }
        String access = JsonParser.parseString(token.body()).getAsJsonObject().get("access_token").getAsString();
        HttpResponse<String> me = http.send(HttpRequest.newBuilder(URI.create(ME))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + access).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (me.statusCode() != 200) {
            return null;
        }
        JsonObject user = JsonParser.parseString(me.body()).getAsJsonObject();
        String name = user.has("global_name") && !user.get("global_name").isJsonNull()
                ? user.get("global_name").getAsString() : user.get("username").getAsString();
        return new Identity(flow, user.get("id").getAsString(), name);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
