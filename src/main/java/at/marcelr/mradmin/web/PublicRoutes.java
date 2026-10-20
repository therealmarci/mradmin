package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.hook.Bans;
import at.marcelr.mradmin.mail.Mailer;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.ticket.WebRequest;
import at.marcelr.mradmin.util.TicketText;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The public forms - no sign-in, so every input is checked and every route is rate limited.
 * <pre>
 *  GET  /api/public/info                 which forms are on, text lengths
 *  GET  /api/public/challenge            spam protection: challenge + proof-of-work difficulty
 *  POST /api/public/contact              {name, email, subject, text, challenge, nonce, website}
 *  POST /api/public/unban/check          {name, code}: the ban and an existing request
 *  POST /api/public/unban                {name, code, text, email?, challenge, nonce, website}
 *  POST /api/public/status               {token} or {name, code}: state and conversation of a request
 *  POST /api/public/status/reply         {token | name+code, text}
 *  POST /api/public/status/appeal        {token | name+code, text}: the one appeal after a rejection
 * </pre>
 * "website" is the honeypot field (invisible on the page, bots fill it in).
 */
public final class PublicRoutes {

    private final WebServer server;
    private final MRAdmin plugin;
    private final PublicRequests requests;

    private PublicRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
        this.requests = server.requests();
    }

    public static void register(WebServer server) {
        PublicRoutes r = new PublicRoutes(server);
        server.open("GET", "/api/public/info", r::info);
        server.open("GET", "/api/public/challenge", r::challenge);
        server.open("POST", "/api/public/contact", r::contact);
        server.open("POST", "/api/public/unban/check", r::check);
        server.open("POST", "/api/public/unban", r::unban);
        server.open("POST", "/api/public/status", r::status);
        server.open("POST", "/api/public/status/reply", r::reply);
        server.open("POST", "/api/public/status/appeal", r::appeal);
    }

    private Settings.RequestSettings settings() {
        return plugin.settings().requests();
    }

    private void limit(Http http, String what, int perMinute) throws Http.WebException {
        if (!server.allow("public-" + what + ":" + http.ip(), perMinute)) {
            throw new Http.WebException(429, "rate-limit");
        }
    }

    // ------------------------------------------------------------------- info

    private void info(Http http) throws Exception {
        limit(http, "info", 30);
        Settings.RequestSettings s = settings();
        http.json(200, Map.of("unban", requests.unbanAvailable(), "contact", requests.contactAvailable(),
                "mail", requests.mailer().enabled() && s.notifyByMail(), "minText", s.minTextLength(),
                "maxText", s.maxTextLength()));
    }

    private void challenge(Http http) throws Exception {
        limit(http, "challenge", 20);
        http.json(200, requests.challenge());
    }

    // ---------------------------------------------------------------- contact

    private void contact(Http http) throws Exception {
        limit(http, "send", 6);
        if (!requests.contactAvailable()) {
            throw new Http.WebException(404, "feature-disabled");
        }
        String name = TicketText.limit(TicketText.clean(http.string("name")).replace('\n', ' '), 32).strip();
        String email = String.valueOf(http.string("email")).strip();
        String subject = TicketText.limit(TicketText.clean(http.string("subject")).replace('\n', ' '), 100).strip();
        String text = text(http);
        if (name.length() < 2) {
            throw new Http.WebException(400, "name-missing");
        }
        if (!Mailer.validAddress(email)) {
            throw new Http.WebException(400, "email-invalid");
        }
        if (subject.length() < 3) {
            throw new Http.WebException(400, "subject-short");
        }
        requests.checkSpam(http, http.string("challenge"), http.string("nonce"), http.string("website"));
        if (server.sync(() -> plugin.tickets().openContactRequests()) >= settings().maxOpenContact()) {
            throw new Http.WebException(429, "contact-full");
        }
        requests.checkHourly(http);
        String token = requests.newToken();
        WebRequest request = new WebRequest(WebRequest.CONTACT, email, token, 0, null, null, 0, 0, null, null);
        Ticket ticket = server.sync(() -> plugin.tickets().createRequest(request, TicketManager.GUEST, name, subject,
                text, null));
        requests.sendReceived(ticket);
        http.json(200, Map.of("token", token));
    }

    // ------------------------------------------------------------------ unban

    /** A checked name + code: the player, the ban id and the ban while it is in force (else null). */
    private record Proof(String name, UUID player, long banId, Bans.Entry ban) {
    }

    private Bans bans() throws Http.WebException {
        Bans bans = requests.unbanAvailable() ? Bans.get(plugin) : null;
        if (bans == null) {
            throw new Http.WebException(404, "feature-disabled");
        }
        return bans;
    }

    /** Name and code from the ban message - the same answer for unknown names and wrong codes. */
    private Proof proof(Http http) throws Exception {
        limit(http, "code", 10);
        Bans bans = bans();
        String name = String.valueOf(http.string("name")).strip();
        if (!PublicRequests.validName(name)) {
            throw new Http.WebException(400, "code-wrong");
        }
        UUID player = requests.lookup(bans, name);
        long banId = player == null ? -1 : requests.verify(player, http.string("code"));
        if (banId < 0) {
            throw new Http.WebException(400, "code-wrong");
        }
        String exact = Bukkit.getOfflinePlayer(player).getName();
        return new Proof(exact == null ? name : exact, player, banId, bans.activeBan(banId));
    }

    private void check(Http http) throws Exception {
        Proof proof = proof(http);
        Ticket existing = server.sync(() -> plugin.tickets().unbanRequest(proof.banId()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", proof.name());
        result.put("ban", proof.ban() == null ? null : ban(proof.ban()));
        result.put("request", existing == null ? null : status(existing, proof.ban() != null));
        result.put("canRequest", proof.ban() != null && existing == null && !requests.used(proof.banId()));
        result.put("used", existing == null && requests.used(proof.banId()));
        http.json(200, result);
    }

    private void unban(Http http) throws Exception {
        limit(http, "send", 6);
        Proof proof = proof(http);
        if (proof.ban() == null) {
            throw new Http.WebException(400, "ban-not-active");
        }
        if (requests.used(proof.banId()) || server.sync(() -> plugin.tickets().unbanRequest(proof.banId())) != null) {
            throw new Http.WebException(400, "already-requested");
        }
        String text = text(http);
        String email = String.valueOf(http.string("email") == null ? "" : http.string("email")).strip();
        if (!email.isEmpty() && !Mailer.validAddress(email)) {
            throw new Http.WebException(400, "email-invalid");
        }
        requests.checkSpam(http, http.string("challenge"), http.string("nonce"), http.string("website"));
        requests.checkHourly(http);

        Bans.Entry ban = proof.ban();
        String operator = operator(ban);
        var m = plugin.messages();
        String note = m.plain("request-ban-note", "id", ban.id(), "reason", ban.reason(), "operator", operator,
                "start", at(ban.start()), "end", ban.end() == 0 ? m.plain("request-ban-permanent") : at(ban.end()));
        String subject = m.plain("request-unban-subject", "name", proof.name());
        String token = requests.newToken();
        WebRequest request = new WebRequest(WebRequest.UNBAN, email, token, ban.id(), ban.reason(), operator,
                ban.start(), ban.end(), WebRequest.STAGE_REQUEST, null);
        Ticket ticket = server.sync(() -> plugin.tickets().createRequest(request, proof.player(), proof.name(), subject,
                text, note));
        requests.markUsed(ban.id());
        requests.sendReceived(ticket);
        http.json(200, Map.of("token", token));
    }

    // ----------------------------------------------------------------- status

    /** The ticket for {token} or {name, code}, with whether its ban is still in force (null = not an unban). */
    private record Found(Ticket ticket, Boolean banActive) {
    }

    private Found find(Http http) throws Exception {
        String token = http.string("token");
        if (token != null && !token.isBlank()) {
            limit(http, "status", 30);
            Ticket ticket = token.length() > 64 ? null : server.sync(() -> plugin.tickets().byToken(token));
            if (ticket == null) {
                throw new Http.WebException(404, "not-found");
            }
            Boolean active = null;
            if (ticket.request().unban()) {
                Bans bans = Bans.get(plugin);
                active = bans == null ? Boolean.valueOf(ticket.request().decision() == null
                        || WebRequest.REJECTED.equals(ticket.request().decision()))
                        : bans.activeBan(ticket.request().banId()) != null;
            }
            return new Found(ticket, active);
        }
        Proof proof = proof(http);
        Ticket ticket = server.sync(() -> plugin.tickets().unbanRequest(proof.banId()));
        if (ticket == null) {
            throw new Http.WebException(404, "not-found");
        }
        return new Found(ticket, proof.ban() != null);
    }

    private void status(Http http) throws Exception {
        Found found = find(http);
        http.json(200, server.sync(() -> status(found.ticket(), Boolean.TRUE.equals(found.banActive()))));
    }

    private void reply(Http http) throws Exception {
        limit(http, "reply", 6);
        Found found = find(http);
        String text = TicketText.limit(TicketText.clean(http.string("text")), settings().maxTextLength());
        if (text.isBlank()) {
            throw new Http.WebException(400, "reply-empty");
        }
        String error = server.sync(() -> plugin.tickets().requesterReply(found.ticket(), text));
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        http.json(200, server.sync(() -> status(found.ticket(), Boolean.TRUE.equals(found.banActive()))));
    }

    private void appeal(Http http) throws Exception {
        limit(http, "reply", 6);
        Found found = find(http);
        String text = text(http);
        String error = server.sync(() -> {
            Ticket t = found.ticket();
            if (!canAppeal(t, Boolean.TRUE.equals(found.banActive()))) {
                return "appeal-not-possible";
            }
            plugin.tickets().appeal(t, text);
            return null;
        });
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        http.json(200, server.sync(() -> status(found.ticket(), true)));
    }

    // ---------------------------------------------------------------- helpers

    private String text(Http http) throws Exception {
        String text = TicketText.limit(TicketText.clean(http.string("text")), settings().maxTextLength()).strip();
        if (text.length() < settings().minTextLength()) {
            throw new Http.WebException(400, "text-short");
        }
        return text;
    }

    private static boolean canAppeal(Ticket t, boolean banActive) {
        WebRequest r = t.request();
        return r.unban() && !t.open() && banActive && WebRequest.REJECTED.equals(r.decision()) && !r.appeal();
    }

    /** Who banned, as the player may see it: the name if the team shows names in tickets, otherwise "Team". */
    private String operator(Bans.Entry ban) {
        if (!plugin.settings().tickets().showStaffNames()) {
            return plugin.messages().plain("ticket-staff-anonymous");
        }
        if (ban.operator() == null) {
            return plugin.messages().plain("request-ban-console");
        }
        String name = Bukkit.getOfflinePlayer(ban.operator()).getName();
        return name == null ? plugin.messages().plain("ticket-staff-anonymous") : name;
    }

    private String at(long epochMillis) {
        return at.marcelr.mradmin.Texts.at(plugin, java.time.Instant.ofEpochMilli(epochMillis));
    }

    private Map<String, Object> ban(Bans.Entry ban) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("id", ban.id());
        b.put("reason", ban.reason());
        b.put("operator", operator(ban));
        b.put("start", ban.start());
        b.put("end", ban.end());
        return b;
    }

    /** What the person sees of their request. Staff names only if the team shows them in tickets. Server thread. */
    private Map<String, Object> status(Ticket t, boolean banActive) {
        WebRequest r = t.request();
        boolean names = plugin.settings().tickets().showStaffNames();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", t.id());
        s.put("kind", r.kind());
        s.put("subject", t.subject());
        s.put("name", t.creatorName());
        s.put("open", t.open());
        s.put("created", t.created().toEpochMilli());
        s.put("email", r.hasEmail() ? Mailer.mask(r.email()) : "");
        if (r.unban()) {
            s.put("stage", r.stage());
            s.put("decision", r.decision());
            s.put("banActive", banActive);
            s.put("ban", Map.of("id", r.banId(), "reason", String.valueOf(r.banReason()),
                    "operator", String.valueOf(r.banOperator()), "start", r.banStart(), "end", r.banEnd()));
            s.put("canAppeal", canAppeal(t, banActive));
        }
        s.put("canReply", t.open());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Ticket.Message m : t.messages()) {
            boolean system = TicketManager.SYSTEM.equals(m.author());
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("mine", !m.staff());
            msg.put("system", system);
            msg.put("name", !m.staff() || system || names ? m.authorName()
                    : plugin.messages().plain("ticket-staff-anonymous"));
            msg.put("text", m.text());
            msg.put("time", m.time().toEpochMilli());
            messages.add(msg);
        }
        s.put("messages", messages);
        return s;
    }
}
