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
 *  POST   /api/login/passkey/start                          open: challenge for a passkey sign-in
 *  POST   /api/login/passkey/finish {request, credential}    open, 10/min per IP
 *  GET    /api/passkeys                                     own passkeys
 *  POST   /api/passkeys/start  {code}                        adding one needs a fresh 2FA code
 *  POST   /api/passkeys/finish {credential, name}
 *  DELETE /api/passkeys/{id}
 *  POST   /api/confirm/passkey                             passkey check for a sensitive step (instead of a code)
 *  POST   /api/account/totp/disable {code | passkey}       2FA off (only with a passkey)
 *  POST   /api/account/totp/start   {passkey}             2FA on again: new secret
 *  POST   /api/account/totp/finish  {code}               ... confirmed, new recovery codes
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
        server.open("POST", "/api/login/passkey/start", r::passkeyLoginStart);
        server.open("POST", "/api/login/passkey/finish", r::passkeyLoginFinish);
        server.route("GET", "/api/passkeys", r::passkeys);
        server.route("POST", "/api/passkeys/start", r::passkeyAddStart);
        server.route("POST", "/api/passkeys/finish", r::passkeyAddFinish);
        server.route("DELETE", "/api/passkeys/{id}", r::passkeyRemove);
        server.route("POST", "/api/confirm/passkey", r::confirmStart);
        server.route("POST", "/api/account/totp/disable", r::totpDisable);
        server.route("POST", "/api/account/totp/start", r::totpStart);
        server.route("POST", "/api/account/totp/finish", r::totpFinish);
    }

    // --------------------------------------------------------- confirmation, 2FA

    /** A passkey check for a sensitive step (then send {request, credential} instead of {code}). */
    private void confirmStart(Http http) throws Exception {
        requirePasskeys();
        if (!server.allow("passkey:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        if (auth.passkeys(http.account().id()).isEmpty()) {
            throw new Http.WebException(400, "need-passkey");
        }
        Map<String, String> start = server.passkeys().startConfirm(http.account().id());
        http.json(200, Map.of("request", start.get("request"),
                "options", com.google.gson.JsonParser.parseString(start.get("options"))));
    }

    /** 2FA off (only with a passkey; then sign-in only with passkeys) - confirmed with a code or a passkey. */
    private void totpDisable(Http http) throws Exception {
        if (!server.allow("passkey:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        if (!server.confirmed(http)) {
            throw new Http.WebException(400, "code-wrong");
        }
        String error = auth.disableTotp(http.account().id());
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        log(http, http.account().id(), http.account().name(), "2fa-off", "");
        http.json(200, Map.of());
    }

    /** 2FA on again, step 1 - confirmed with a passkey (there is no code without 2FA): the new secret for the app. */
    private void totpStart(Http http) throws Exception {
        if (!server.allow("passkey:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        if (!server.confirmed(http)) {
            throw new Http.WebException(400, "passkey-failed");
        }
        Map<String, Object> start = auth.startTotp(http.account().id());
        if (start == null) {
            throw new Http.WebException(400, "totp-on");
        }
        http.json(200, start);
    }

    /** Step 2: the first code from the app; answers with new recovery codes. */
    private void totpFinish(Http http) throws Exception {
        if (!server.allow("passkey:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        List<String> codes = auth.enableTotp(http.account().id(), http.string("code"));
        if (codes == null) {
            throw new Http.WebException(400, "code-wrong");
        }
        log(http, http.account().id(), http.account().name(), "2fa-on", "");
        http.json(200, Map.of("recoveryCodes", codes));
    }

    // ---------------------------------------------------------------- passkeys

    private void requirePasskeys() throws Http.WebException {
        if (!server.passkeys().enabled()) {
            throw new Http.WebException(404, "feature-disabled");
        }
    }

    /** Sign-in with a passkey, step 1: the browser gets a challenge (no name needed - the passkey knows it). */
    private void passkeyLoginStart(Http http) throws Exception {
        limit(http, "login");
        requirePasskeys();
        Map<String, String> start = server.passkeys().startAssertion();
        http.json(200, Map.of("request", start.get("request"),
                "options", com.google.gson.JsonParser.parseString(start.get("options"))));
    }

    /** Step 2: the signed challenge - on success the browser is signed in like with password + code. */
    private void passkeyLoginFinish(Http http) throws Exception {
        limit(http, "login");
        requirePasskeys();
        var credential = http.body().get("credential");
        WebAuth.Result result = credential == null ? WebAuth.Result.fail("passkey-failed")
                : server.passkeys().finishAssertion(http.string("request"), credential.toString(), http.storedIp(),
                        http.header("User-Agent"));
        if (!result.ok()) {
            log(http, null, "", "login-failed", "passkey: " + result.error());
            http.error(result.error().equals("locked") ? 423 : 401, result.error());
            return;
        }
        signedIn(http, result, "login-passkey");
        http.json(200, Map.of("name", auth.account(result.session().account()).name()));
    }

    private void passkeys(Http http) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WebAuth.Passkey p : server.passkeys().list(http.account().id())) {
            list.add(Map.of("id", p.id(), "name", p.name(), "created", p.created(), "lastUsed", p.lastUsed()));
        }
        http.json(200, Map.of("enabled", server.passkeys().enabled(), "passkeys", list));
    }

    /** Adding a passkey needs a fresh 2FA code (or an existing passkey) - a stolen session alone cannot add one. */
    private void passkeyAddStart(Http http) throws Exception {
        requirePasskeys();
        if (!server.allow("passkey:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        if (!server.confirmed(http)) {
            throw new Http.WebException(400, "code-wrong");
        }
        http.json(200, Map.of("options", com.google.gson.JsonParser.parseString(
                server.passkeys().startRegistration(http.account().id()))));
    }

    private void passkeyAddFinish(Http http) throws Exception {
        requirePasskeys();
        var credential = http.body().get("credential");
        String error = credential == null ? "passkey-failed"
                : server.passkeys().finishRegistration(http.account().id(), credential.toString(), http.string("name"));
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        log(http, http.account().id(), http.account().name(), "passkey-add", String.valueOf(http.string("name")));
        http.json(200, Map.of());
    }

    private void passkeyRemove(Http http) throws Exception {
        String error = auth.removePasskey(http.account().id(), http.param("id"));
        if (error != null) {
            throw new Http.WebException(error.equals("not-found") ? 404 : 400, error);
        }
        log(http, http.account().id(), http.account().name(), "passkey-remove", "");
        http.json(200, Map.of());
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
        may.put("unban", tickets && perms.has(account.id(), "libertybans.ban.undo.target.uuid"));
        may.put("mail", server.requests().mailer().enabled());
        may.put("status", plugin.feature(Feature.SERVER_STATUS) && perms.has(account.id(), Perms.STATUS));
        may.put("health", plugin.feature(Feature.SERVER_STATUS) && perms.has(account.id(), Perms.STATUS_HEALTH));
        may.put("maintenance", plugin.feature(Feature.MAINTENANCE) && perms.has(account.id(), Perms.MAINTENANCE_VIEW));
        may.put("maintenanceManage", plugin.feature(Feature.MAINTENANCE) && perms.has(account.id(), Perms.MAINTENANCE_MANAGE));
        may.put("announce", plugin.feature(Feature.ANNOUNCE) && perms.has(account.id(), Perms.ANNOUNCE));
        // Modules (MRchunks, MRsleep, MRpvp ...): "<id>" = may see it, "<id><Key>" for each of its permission keys,
        // e.g. mrchunksManage. Every key also needs viewing - the routes check the same.
        plugin.modules().webModules().forEach((id, web) -> {
            boolean view = ModuleRoutes.may(server, account.id(), id, "view");
            may.put(id, view);
            for (String key : web.permissions().keySet()) {
                may.put(id + Character.toUpperCase(key.charAt(0)) + key.substring(1),
                        view && ModuleRoutes.may(server, account.id(), id, key));
            }
        });
        may.put("players", perms.has(account.id(), Perms.PLAYER_VIEW));
        boolean console = ConsoleRoutes.enabled(plugin) && perms.has(account.id(), Perms.WEB_CONSOLE_VIEW);
        may.put("console", console);
        may.put("consoleExecute", console && perms.has(account.id(), Perms.WEB_CONSOLE_EXECUTE));
        may.put("audit", perms.has(account.id(), Perms.WEB_AUDIT));
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("uuid", account.id().toString());
        me.put("name", account.name());
        me.put("lastLogin", account.lastLogin());
        me.put("recoveryLeft", account.recoveryLeft());
        me.put("passkeys", auth.passkeys(account.id()).size());
        me.put("totp", auth.hasTotp(account.id()));
        String[] discordLink = auth.discord(account.id());
        me.put("discord", discordLink == null ? null : discordLink[1]);
        me.put("discordAvailable", server.discord().enabled());
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
