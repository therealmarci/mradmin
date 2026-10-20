package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.web.auth.DiscordLogin;
import at.marcelr.mradmin.web.auth.WebAuth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Discord in the web interface: sign in, link to the own account, confirm a sensitive step.
 * <pre>
 *  GET  /api/login/options                 open: which sign-in methods there are (Discord, passkeys)
 *  POST /api/discord/start {mode, returnTo}  login (open) | link | confirm (signed in) -> {url}
 *  GET  /api/discord/callback              open: Discord comes back here -> on to /discord (the page finishes)
 *  POST /api/discord/finish {state, code}  open: exchanges the code; login sets the cookie
 *  POST /api/discord/unlink
 * </pre>
 * The callback only forwards to the interface: the session cookie is SameSite=Strict, so the browser does not send
 * it on the way back from discord.com - the page sends it with the next (same-site) request.
 */
public final class DiscordRoutes {

    private static final Set<String> MODES = Set.of("login", "link", "confirm");

    private final WebServer server;
    private final MRAdmin plugin;
    private final WebAuth auth;

    private DiscordRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
        this.auth = server.auth();
    }

    public static void register(WebServer server) {
        DiscordRoutes r = new DiscordRoutes(server);
        server.open("GET", "/api/login/options", r::options);
        server.open("POST", "/api/discord/start", r::start);
        server.open("GET", "/api/discord/callback", r::callback);
        server.open("POST", "/api/discord/finish", r::finish);
        server.route("POST", "/api/discord/unlink", r::unlink);
    }

    private DiscordLogin discord() throws Http.WebException {
        DiscordLogin discord = server.discord();
        if (!discord.enabled()) {
            throw new Http.WebException(404, "feature-disabled");
        }
        return discord;
    }

    private void options(Http http) throws Exception {
        http.json(200, Map.of("discord", server.discord().enabled(), "passkeys", plugin.settings().web().passkeys()));
    }

    /** The signed-in session of this browser (open routes read the cookie themselves), or null. */
    private WebAuth.Session session(Http http) {
        return auth.session(http.cookie(WebServer.COOKIE), http.storedIp());
    }

    private void start(Http http) throws Exception {
        if (!server.allow("login:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        DiscordLogin discord = discord();
        String mode = http.string("mode");
        if (mode == null || !MODES.contains(mode)) {
            throw new Http.WebException(400, "unknown-action");
        }
        String returnTo = http.string("returnTo");
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
            returnTo = "/";
        }
        WebAuth.Session session = null;
        if (!mode.equals("login")) {
            session = session(http);
            if (session == null) {
                throw new Http.WebException(401, "signed-out");
            }
        }
        String url = discord.start(mode, session == null ? null : session.account(), session == null ? null : session.id(),
                returnTo);
        http.json(200, Map.of("url", url));
    }

    /** From Discord: only known flows are passed on to the page (which then finishes with its cookie). */
    private void callback(Http http) throws Exception {
        String query = http.exchange().getRequestURI().getRawQuery();
        Map<String, String> q = new java.util.HashMap<>();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    q.put(pair.substring(0, eq), java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        String state = q.get("state");
        String target;
        if (q.containsKey("error") || !server.discord().known(state)) {
            target = "/discord#error=" + enc(q.getOrDefault("error", "expired"));
        } else {
            target = "/discord#state=" + enc(state) + "&code=" + enc(q.getOrDefault("code", ""));
        }
        http.exchange().getResponseHeaders().set("Location", target);
        http.send(302, "text/plain; charset=utf-8", new byte[0], "no-store");
    }

    private void finish(Http http) throws Exception {
        if (!server.allow("login:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        DiscordLogin discord = discord();
        DiscordLogin.Identity who = discord.finish(http.string("state"), http.string("code"));
        if (who == null) {
            throw new Http.WebException(400, "discord-failed");
        }
        DiscordLogin.Pending flow = who.flow();
        switch (flow.mode()) {
            case "login" -> {
                WebAuth.Account account = auth.byDiscord(who.id());
                if (account == null) {
                    audit(http, null, "", "login-failed", "discord: not linked");
                    throw new Http.WebException(401, "discord-unknown");
                }
                WebAuth.Result result = auth.discordLogin(account.id(), who.name(), http.storedIp(), http.header("User-Agent"));
                if (!result.ok()) {
                    throw new Http.WebException(result.error().equals("locked") ? 423 : 401, result.error());
                }
                var s = plugin.settings().web();
                http.setCookie(WebServer.COOKIE, result.token(), s.sessionDays() * 86_400L, s.secure());
                audit(http, account.id(), account.name(), "login-discord", who.name());
                plugin.discord().log("web-login", "player", account.name(), "ip", http.storedIp(),
                        "device", "Discord (" + who.name() + ")");
                http.json(200, Map.of("mode", "login", "returnTo", flow.returnTo()));
            }
            case "link", "confirm" -> {
                // Only the session that started it - another browser cannot finish someone else's link.
                WebAuth.Session session = session(http);
                if (session == null || !session.id().equals(flow.session()) || !session.account().equals(flow.account())) {
                    throw new Http.WebException(403, "discord-failed");
                }
                WebAuth.Account account = auth.account(session.account());
                if (flow.mode().equals("link")) {
                    String error = auth.linkDiscord(session.account(), who.id(), who.name());
                    if (error != null) {
                        throw new Http.WebException(400, error);
                    }
                    audit(http, account.id(), account.name(), "discord-link", who.name());
                } else {
                    String[] linked = auth.discord(session.account());
                    if (linked == null || !linked[0].equals(who.id())) {
                        throw new Http.WebException(403, "discord-other");
                    }
                    server.discordConfirmed(session.id());
                }
                http.json(200, Map.of("mode", flow.mode(), "returnTo", flow.returnTo()));
            }
            default -> throw new Http.WebException(400, "unknown-action");
        }
    }

    private void unlink(Http http) throws Exception {
        String error = auth.unlinkDiscord(http.account().id());
        if (error != null) {
            throw new Http.WebException(error.equals("not-found") ? 404 : 400, error);
        }
        audit(http, http.account().id(), http.account().name(), "discord-unlink", "");
        http.json(200, Map.of());
    }

    private void audit(Http http, java.util.UUID account, String name, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), account, name, http.storedIp(), action, detail));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
