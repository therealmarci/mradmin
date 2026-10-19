package at.marcelr.mradmin.web;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.web.auth.WebAuth;
import at.marcelr.mradmin.web.auth.WebPermissions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sign-in, setup, the own account and its sessions, and the log of web actions.
 * <pre>
 *  POST   /api/login            {name, password, code}      open, 10/min per IP
 *  POST   /api/setup/info       {token}                     open
 *  POST   /api/setup/finish     {token, password, code}     open, 10/min per IP
 *  POST   /api/logout
 *  GET    /api/me
 *  GET    /api/sessions
 *  DELETE /api/sessions/{id}
 *  POST   /api/sessions/others                              signs out all other browsers
 *  POST   /api/account/recovery {code}                      new recovery codes
 *  GET    /api/audit                                        mradmin.web.audit
 * </pre>
 */
public final class AuthRoutes {

    public static final String PERM_MANAGE = at.marcelr.mradmin.Perms.WEB_MANAGE;

    private final WebServer server;
    private final MRAdmin plugin;
    private final WebAuth auth;

    private AuthRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
        this.auth = server.auth();
    }

    public static void register(WebServer server) {
        AuthRoutes r = new AuthRoutes(server);
        server.open("POST", "/api/login", r::login);
        server.open("POST", "/api/setup/info", r::setupInfo);
        server.open("POST", "/api/setup/finish", r::setupFinish);
        server.route("POST", "/api/logout", r::logout);
        server.route("GET", "/api/me", r::me);
        server.route("GET", "/api/sessions", r::sessions);
        server.route("DELETE", "/api/sessions/{id}", r::revoke);
        server.route("POST", "/api/sessions/others", r::revokeOthers);
        server.route("POST", "/api/account/recovery", r::recovery);
        server.route("GET", "/api/audit", r::audit);
    }

    // ------------------------------------------------------------------ public

    private void login(Http http) throws Exception {
        limit(http, "login");
        String name = http.string("name");
        WebAuth.Result result = auth.login(name, http.string("password"), http.string("code"), http.storedIp(),
                http.header("User-Agent"));
        if (!result.ok()) {
            log(http, null, name, "login-failed", result.error());
            http.error(result.error().equals("locked") ? 423 : 401, result.error());
            return;
        }
        signedIn(http, result, "login");
        http.json(200, Map.of("name", auth.account(result.session().account()).name()));
    }

    private void setupInfo(Http http) throws Exception {
        limit(http, "setup");
        Map<String, Object> info = auth.setupInfo(http.string("token"));
        if (info == null) {
            http.error(404, "setup-invalid");
            return;
        }
        http.json(200, info);
    }

    private void setupFinish(Http http) throws Exception {
        limit(http, "setup");
        WebAuth.Result result = auth.finishSetup(http.string("token"), http.string("password"), http.string("code"),
                http.storedIp(), http.header("User-Agent"));
        if (!result.ok()) {
            http.error(400, result.error());
            return;
        }
        signedIn(http, result, "setup");
        http.json(200, Map.of("name", auth.account(result.session().account()).name(),
                "recoveryCodes", result.recoveryCodes()));
    }

    private void signedIn(Http http, WebAuth.Result result, String action) {
        var s = plugin.settings().web();
        http.setCookie(WebServer.COOKIE, result.token(), s.sessionDays() * 86_400L, s.secure());
        WebAuth.Account account = auth.account(result.session().account());
        log(http, account.id(), account.name(), action, device(http.header("User-Agent")));
        plugin.discord().log(action.equals("setup") ? "web-setup" : "web-login",
                "player", account.name(), "ip", http.storedIp(), "device", device(http.header("User-Agent")));
    }

    /** Sign-in and setup: at most 10 tries per minute and IP (password guessing). */
    private void limit(Http http, String what) throws Http.WebException {
        if (!server.allow(what + ":" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
    }

    // ----------------------------------------------------------------- account

    private void logout(Http http) throws Exception {
        auth.logout(http.cookie(WebServer.COOKIE));
        http.setCookie(WebServer.COOKIE, "", 0, plugin.settings().web().secure());
        log(http, http.account().id(), http.account().name(), "logout", "");
    }

    /** Who is signed in and what they may see - the interface shows only what fits. */
    private void me(Http http) throws Exception {
        WebAuth.Account account = http.account();
        WebPermissions perms = server.permissions();
        Map<String, Object> may = new LinkedHashMap<>();
        boolean tickets = plugin.feature(Feature.TICKETS);
        may.put("tickets", tickets && perms.has(account.id(), Perms.TICKETS_VIEW));
        may.put("ticketsReply", tickets && perms.has(account.id(), Perms.TICKETS_REPLY));
        may.put("ticketsClaim", tickets && perms.has(account.id(), Perms.TICKETS_CLAIM));
        may.put("ticketsClose", tickets && perms.has(account.id(), Perms.TICKETS_CLOSE));
        may.put("ticketsReopen", tickets && perms.has(account.id(), Perms.TICKETS_REOPEN));
        may.put("status", plugin.feature(Feature.SERVER_STATUS) && perms.has(account.id(), Perms.STATUS));
        may.put("maintenance", plugin.feature(Feature.MAINTENANCE) && perms.has(account.id(), Perms.MAINTENANCE_VIEW));
        may.put("audit", perms.has(account.id(), Perms.WEB_AUDIT));
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("uuid", account.id().toString());
        me.put("name", account.name());
        me.put("lastLogin", account.lastLogin());
        me.put("recoveryLeft", account.recoveryLeft());
        me.put("version", plugin.updateChecker().currentVersion());
        me.put("may", may);
        http.json(200, me);
    }

    private void sessions(Http http) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WebAuth.Session s : auth.sessions(http.account().id())) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", s.id());
            entry.put("created", s.created());
            entry.put("lastSeen", s.lastSeen());
            entry.put("expires", s.expires());
            entry.put("ip", s.ip());
            entry.put("device", device(s.agent()));
            entry.put("current", s.id().equals(http.session().id()));
            list.add(entry);
        }
        http.json(200, list);
    }

    private void revoke(Http http) throws Exception {
        if (!auth.revoke(http.account().id(), http.param("id"))) {
            http.error(404, "not-found");
            return;
        }
        log(http, http.account().id(), http.account().name(), "session-revoked", http.param("id"));
    }

    private void revokeOthers(Http http) throws Exception {
        int count = auth.revokeAll(http.account().id(), http.session().id());
        log(http, http.account().id(), http.account().name(), "sessions-revoked", String.valueOf(count));
        http.json(200, Map.of("count", count));
    }

    private void recovery(Http http) throws Exception {
        List<String> codes = auth.newRecoveryCodes(http.account().id(), http.string("code"));
        if (codes == null) {
            http.error(400, "code-wrong");
            return;
        }
        log(http, http.account().id(), http.account().name(), "recovery-codes", "");
        http.json(200, Map.of("recoveryCodes", codes));
    }

    private void audit(Http http) throws Exception {
        if (!server.permissions().has(http.account().id(), Perms.WEB_AUDIT)) {
            http.error(403, "no-permission");
            return;
        }
        http.json(200, plugin.storage().recentAudit(200));
    }

    // ----------------------------------------------------------------- helpers

    private void log(Http http, java.util.UUID account, String name, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), account, name, http.storedIp(), action, detail));
    }

    /** "Firefox on Windows" instead of the whole user agent. */
    public static String device(String agent) {
        if (agent == null || agent.isEmpty()) {
            return "?";
        }
        String browser = agent.contains("Edg/") ? "Edge" : agent.contains("OPR/") ? "Opera" : agent.contains("Firefox/") ? "Firefox"
                : agent.contains("Chrome/") ? "Chrome" : agent.contains("Safari/") ? "Safari" : "Browser";
        String os = agent.contains("Android") ? "Android" : agent.contains("iPhone") ? "iPhone" : agent.contains("iPad") ? "iPad"
                : agent.contains("Windows") ? "Windows" : agent.contains("Mac OS") ? "macOS" : agent.contains("Linux") ? "Linux" : "?";
        return browser + " / " + os;
    }
}
