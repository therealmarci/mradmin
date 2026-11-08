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
 * Sign-in, the own account (its sign-ins and browsers) and the log of web actions. Since 1.5 there is one account
 * per player: in with the code from /web (SelfRoutes), on top an own sign-in - password (name or confirmed e-mail),
 * passkey or Discord, optionally with 2FA.
 * <pre>
 *  POST   /api/login            {name, password, code?}      open, 10/min per IP (name = player name or e-mail)
 *  POST   /api/login/forgot     {name}                       open: mail with a link for a new password
 *  POST   /api/login/reset/check {token}                     open: does the link still work?
 *  POST   /api/login/reset      {token, password}            open: new password, other browsers signed out
 *  POST   /api/account/email/confirm {token}                 open: link from the mail confirms the address
 *  POST   /api/login/passkey/start                           open: challenge for a passkey sign-in
 *  POST   /api/login/passkey/finish {request, credential}    open, 10/min per IP
 * own account (any signed-in browser):
 *  POST   /api/logout
 *  GET    /api/me                                            who, team or not, what they may see
 *  GET    /api/account/logins                                own sign-ins
 *  POST   /api/account/password {password, current? | confirm}  set / change (change needs a confirmation)
 *  DELETE /api/account/password {confirm}
 *  POST   /api/account/email {email}                         mail with a confirmation link
 *  DELETE /api/account/email
 *  GET    /api/sessions · DELETE /api/sessions/{id} · POST /api/sessions/others
 *  POST   /api/account/recovery {code}                       new recovery codes
 *  GET    /api/passkeys · POST /api/passkeys/start {confirm?} · POST /api/passkeys/finish · DELETE /api/passkeys/{id}
 *  POST   /api/confirm/passkey                               passkey check for a sensitive step
 *  POST   /api/account/totp/disable {confirm} · /totp/start {confirm?} · /totp/finish {code}
 * administration:
 *  GET    /api/audit                                         mradmin.web.audit
 * </pre>
 * "confirm" = one of the own sign-ins (see WebServer#confirmed): 2FA code, passkey, Discord or the password. The
 * first own sign-in can be added without one - the /web sign-in is enough there.
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
        server.open("POST", "/api/login/forgot", r::forgot);
        server.open("POST", "/api/login/reset/check", r::resetCheck);
        server.open("POST", "/api/login/reset", r::reset);
        server.open("POST", "/api/account/email/confirm", r::emailConfirm);
        server.open("POST", "/api/login/passkey/start", r::passkeyLoginStart);
        server.open("POST", "/api/login/passkey/finish", r::passkeyLoginFinish);
        server.account("POST", "/api/logout", r::logout);
        server.account("GET", "/api/me", r::me);
        server.account("GET", "/api/account/logins", r::logins);
        server.account("POST", "/api/account/password", r::passwordSet);
        server.account("DELETE", "/api/account/password", r::passwordRemove);
        server.account("POST", "/api/account/email", r::emailSet);
        server.account("DELETE", "/api/account/email", r::emailRemove);
        server.account("GET", "/api/sessions", r::sessions);
        server.account("DELETE", "/api/sessions/{id}", r::revoke);
        server.account("POST", "/api/sessions/others", r::revokeOthers);
        server.account("POST", "/api/account/recovery", r::recovery);
        server.route("GET", "/api/audit", r::audit);
        server.account("GET", "/api/passkeys", r::passkeys);
        server.account("POST", "/api/passkeys/start", r::passkeyAddStart);
        server.account("POST", "/api/passkeys/finish", r::passkeyAddFinish);
        server.account("DELETE", "/api/passkeys/{id}", r::passkeyRemove);
        server.account("POST", "/api/confirm/passkey", r::confirmStart);
        server.account("POST", "/api/account/totp/disable", r::totpDisable);
        server.account("POST", "/api/account/totp/start", r::totpStart);
        server.account("POST", "/api/account/totp/finish", r::totpFinish);
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

    /** 2FA off - confirmed with an own sign-in (code, passkey, Discord or password). */
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

    /** 2FA on, step 1 (confirmed with an own sign-in, if there is one): the new secret for the app. */
    private void totpStart(Http http) throws Exception {
        if (!server.allow("passkey:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        requireConfirm(http);
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

    /** Adding a passkey: the first own sign-in freely, further ones confirmed with an existing one. */
    private void passkeyAddStart(Http http) throws Exception {
        requirePasskeys();
        if (!server.allow("passkey:" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        requireConfirm(http);
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
            if (!result.error().equals("code-needed")) {
                log(http, null, name, "login-failed", result.error());
            }
            http.error(result.error().equals("locked") ? 423 : 401, result.error());
            return;
        }
        signedIn(http, result, "login");
        http.json(200, Map.of("name", auth.account(result.session().account()).name()));
    }

    private void signedIn(Http http, WebAuth.Result result, String action) {
        http.setCookie(WebServer.COOKIE, result.token(), server.playerSessions().cookieSeconds(), plugin.settings().web().secure());
        WebAuth.Account account = auth.account(result.session().account());
        log(http, account.id(), account.name(), action, device(http.header("User-Agent")));
        plugin.discord().log("web-login",
                "player", account.name(), "ip", http.storedIp(), "device", device(http.header("User-Agent")));
    }

    /** Sign-in: at most 10 tries per minute and IP (password guessing). */
    private void limit(Http http, String what) throws Http.WebException {
        if (!server.allow(what + ":" + http.ip(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
    }

    // --------------------------------------------------------- own sign-ins

    /**
     * Further own sign-ins (or changing one) need a confirmation with an existing one - the first can be added with
     * the /web sign-in alone.
     */
    private void requireConfirm(Http http) throws Exception {
        if (http.account().ownLogin() && !server.confirmed(http)) {
            throw new Http.WebException(400, "confirm-failed");
        }
    }

    private void logins(Http http) throws Exception {
        WebAuth.Account account = http.account();
        var web = plugin.settings().web();
        List<Map<String, Object>> keys = new ArrayList<>();
        for (WebAuth.Passkey p : auth.passkeys(account.id())) {
            keys.add(Map.of("id", p.id(), "name", p.name(), "created", p.created(), "lastUsed", p.lastUsed()));
        }
        String[] discordLink = auth.discord(account.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", account.name());
        result.put("email", account.email());
        result.put("password", account.hasPassword());
        result.put("totp", auth.hasTotp(account.id()));
        result.put("recoveryLeft", account.recoveryLeft());
        result.put("passkeys", keys);
        result.put("discord", discordLink == null ? null : discordLink[1]);
        result.put("ownLogin", account.ownLogin());
        result.put("available", Map.of("password", web.password(), "passkeys", server.passkeys().enabled(),
                "discord", server.discord().enabled(), "mail", server.requests().mailer().enabled()));
        result.put("minPassword", at.marcelr.mradmin.web.auth.Passwords.MIN_LENGTH);
        http.json(200, result);
    }

    /** Sets the password (first time: free; change: with the current password or another confirmation). */
    private void passwordSet(Http http) throws Exception {
        if (!plugin.settings().web().password()) {
            throw new Http.WebException(404, "feature-disabled");
        }
        limit(http, "password");
        WebAuth.Account account = http.account();
        String current = http.string("current");
        boolean ok = !account.ownLogin()
                || (current != null && !current.isEmpty() ? auth.checkPassword(account.id(), current) : server.confirmed(http));
        if (!ok) {
            throw new Http.WebException(400, "confirm-failed");
        }
        String error = auth.setPassword(account.id(), http.string("password"));
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        log(http, account.id(), account.name(), "password-set", "");
        http.json(200, Map.of());
    }

    private void passwordRemove(Http http) throws Exception {
        limit(http, "password");
        requireConfirm(http);
        auth.removePassword(http.account().id());
        log(http, http.account().id(), http.account().name(), "password-remove", "");
        http.json(200, Map.of());
    }

    /** A new e-mail address: a mail with a confirmation link; the address counts only after the click. */
    private void emailSet(Http http) throws Exception {
        var mailer = server.requests().mailer();
        if (!mailer.enabled()) {
            throw new Http.WebException(400, "mail-off");
        }
        if (!server.allow("email:" + http.account().id(), 3)) {
            throw new Http.WebException(429, "rate-limit");
        }
        WebAuth.Account account = http.account();
        String email = http.string("email");
        String[] token = auth.requestEmail(account.id(), email);
        if (token[1] != null) {
            throw new Http.WebException(400, token[1]);
        }
        String link = baseUrl() + "/email?token=" + token[0];
        sendMail(email.trim(), "mail-email-confirm", account.name(), link);
        log(http, account.id(), account.name(), "email-requested", at.marcelr.mradmin.mail.Mailer.mask(email.trim()));
        http.json(200, Map.of("sent", true));
    }

    private void emailRemove(Http http) throws Exception {
        auth.removeEmail(http.account().id());
        log(http, http.account().id(), http.account().name(), "email-remove", "");
        http.json(200, Map.of());
    }

    private void emailConfirm(Http http) throws Exception {
        limit(http, "email-confirm");
        String name = auth.confirmEmail(http.string("token"));
        if (name == null) {
            throw new Http.WebException(400, "link-invalid");
        }
        http.json(200, Map.of("name", name));
    }

    /** "Forgot password": always the same answer, whether the name exists or not. */
    private void forgot(Http http) throws Exception {
        limit(http, "forgot");
        var mailer = server.requests().mailer();
        if (!mailer.enabled()) {
            throw new Http.WebException(400, "mail-off");
        }
        Object[] reset = auth.requestReset(http.string("name"));
        if (reset != null) {
            WebAuth.Account account = (WebAuth.Account) reset[0];
            sendMail(account.email(), "mail-reset", account.name(), baseUrl() + "/reset?token=" + reset[1]);
            log(http, account.id(), account.name(), "password-forgot", "");
        }
        http.json(200, Map.of("sent", true));
    }

    private void resetCheck(Http http) throws Exception {
        limit(http, "forgot");
        http.json(200, Map.of("valid", auth.resetValid(http.string("token"))));
    }

    private void reset(Http http) throws Exception {
        limit(http, "forgot");
        String error = auth.finishReset(http.string("token"), http.string("password"));
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        http.json(200, Map.of());
    }

    /** The public address of the web interface (links in mails). */
    private String baseUrl() throws Http.WebException {
        String url = plugin.settings().web().publicUrl();
        if (url.isEmpty()) {
            throw new Http.WebException(400, "no-public-url");
        }
        return url;
    }

    /** Mails go to people outside the game - in the server language. */
    private void sendMail(String to, String key, String name, String link) {
        var m = plugin.messages();
        m.server(() -> {
            Object[] values = {"name", name, "link", link, "server", m.plain("mail-server-name"),
                    "minutes", WebAuth.MAIL_LINK_MILLIS / 60_000L};
            server.requests().mailer().send(to, m.plain(key + "-subject", values), m.plain(key + "-body", values));
            return null;
        });
    }

    // ----------------------------------------------------------------- account

    private void logout(Http http) throws Exception {
        auth.logout(http.cookie(WebServer.COOKIE));
        auth.logout(http.cookie(WebServer.PLAYER_COOKIE));
        http.setCookie(WebServer.COOKIE, "", 0, plugin.settings().web().secure());
        http.setCookie(WebServer.PLAYER_COOKIE, "", 0, plugin.settings().web().secure());
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
        may.put("announceTemplates", plugin.feature(Feature.ANNOUNCE) && perms.has(account.id(), Perms.ANNOUNCE_TEMPLATES));
        may.put("news", perms.has(account.id(), Perms.NEWS));
        may.put("selfPage", plugin.settings().playerWeb().enabled() && perms.has(account.id(), Perms.WEB_SELF));
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
        // Overview of all punishments (LibertyBans): like its /banlist, /mutelist and history.
        may.put("punishments", at.marcelr.mradmin.hook.Bans.get(plugin) != null && perms.has(account.id(), Perms.PLAYER_VIEW)
                && perms.has(account.id(), "libertybans.commands")
                && (perms.has(account.id(), "libertybans.list.banlist") || perms.has(account.id(), "libertybans.list.mutelist")
                || perms.has(account.id(), "libertybans.list.history")));
        boolean console = ConsoleRoutes.enabled(plugin) && perms.has(account.id(), Perms.WEB_CONSOLE_VIEW);
        may.put("console", console);
        may.put("consoleExecute", console && perms.has(account.id(), Perms.WEB_CONSOLE_EXECUTE));
        may.put("audit", perms.has(account.id(), Perms.WEB_AUDIT));
        boolean versions = plugin.versions().enabled();
        may.put("versions", versions && VersionRoutes.mayView(server, account.id()));
        may.put("versionsInstall", versions && perms.has(account.id(), Perms.VERSIONS_INSTALL));
        may.put("mapAdmin", plugin.map() != null && perms.has(account.id(), Perms.MAP_ADMIN));
        may.put("config", perms.has(account.id(), Perms.CONFIG));
        may.put("serverRestart", versions && plugin.restarts().enabled() && perms.has(account.id(), Perms.SERVER_RESTART));
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
        // Team: the permission; teamReady: also an own sign-in (and 2FA if web.login.require-2fa) - only then the
        // administration opens (the routes check it too).
        boolean team = perms.has(account.id(), WebPermissions.PERM_WEB);
        me.put("team", team);
        me.put("teamReady", team && auth.teamReady(account.id()));
        me.put("require2fa", plugin.settings().web().require2fa());
        me.put("ownLogin", account.ownLogin());
        me.put("version", plugin.updateChecker().currentVersion());
        me.put("may", may);
        http.json(200, me);
    }

    private void sessions(Http http) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (at.marcelr.mradmin.web.self.PlayerSessions.Session s : auth.sessions(http.account().id())) {
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
