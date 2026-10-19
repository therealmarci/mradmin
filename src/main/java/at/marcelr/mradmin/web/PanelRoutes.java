package at.marcelr.mradmin.web;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.util.TicketText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The team's pages: server overview, live updates and tickets. Every action has its own permission
 * (mradmin.tickets.view, .reply, .claim, .close, .reopen; mradmin.status; mradmin.maintenance.view).
 * <pre>
 *  GET  /api/overview                          server state (mradmin.status), maintenance, ticket counts
 *  GET  /api/events                            live updates (Server-Sent Events)
 *  GET  /api/tickets?state=open|closed         ticket list                    (mradmin.report.manage)
 *  GET  /api/tickets/categories
 *  GET  /api/tickets/{id}                      one ticket with its messages (marks it read)
 *  POST /api/tickets/{id}/reply   {text}
 *  POST /api/tickets/{id}/claim                claim / release
 *  POST /api/tickets/{id}/close   {text?}      optional last message, then closed
 *  POST /api/tickets/{id}/reopen
 * </pre>
 * Ticket data lives on the server thread, so every ticket call runs there briefly ({@link WebServer#sync}).
 */
public final class PanelRoutes {


    private final WebServer server;
    private final MRAdmin plugin;

    private PanelRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        PanelRoutes r = new PanelRoutes(server);
        server.route("GET", "/api/overview", r::overview);
        server.route("GET", "/api/events", r::events);
        server.route("GET", "/api/tickets", r::tickets);
        server.route("GET", "/api/tickets/categories", r::categories);
        server.route("GET", "/api/tickets/{id}", r::ticket);
        server.route("POST", "/api/tickets/{id}/reply", r::reply);
        server.route("POST", "/api/tickets/{id}/claim", r::claim);
        server.route("POST", "/api/tickets/{id}/close", r::close);
        server.route("POST", "/api/tickets/{id}/reopen", r::reopen);
    }

    private boolean may(Http http, String permission) {
        return server.permissions().has(http.account().id(), permission);
    }

    // --------------------------------------------------------------- overview

    /** The parts of the snapshot the member may see. */
    private void overview(Http http) throws Exception {
        Map<String, Object> snapshot = server.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", snapshot.getOrDefault("time", System.currentTimeMillis()));
        if (plugin.feature(Feature.SERVER_STATUS) && may(http, Perms.STATUS)) {
            result.put("server", snapshot.get("server"));
        }
        if (snapshot.containsKey("maintenance") && may(http, Perms.MAINTENANCE_VIEW)) {
            result.put("maintenance", snapshot.get("maintenance"));
        }
        if (snapshot.containsKey("tickets") && may(http, Perms.TICKETS_VIEW)) {
            result.put("tickets", snapshot.get("tickets"));
        }
        http.json(200, result);
    }

    private void events(Http http) throws Exception {
        server.events().stream(http, plugin.feature(Feature.TICKETS) && may(http, Perms.TICKETS_VIEW));
    }

    // ---------------------------------------------------------------- tickets

    private void requireTickets(Http http) throws Http.WebException {
        if (!plugin.feature(Feature.TICKETS)) {
            throw new Http.WebException(404, "feature-disabled");
        }
        if (!may(http, Perms.TICKETS_VIEW)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private void require(Http http, String permission) throws Http.WebException {
        if (!may(http, permission)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private void tickets(Http http) throws Exception {
        requireTickets(http);
        String query = http.exchange().getRequestURI().getQuery();
        boolean open = query == null || !query.contains("state=closed");
        List<Map<String, Object>> list = server.sync(() -> {
            TicketManager tickets = plugin.tickets();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Ticket t : tickets.list(null, open, null)) {
                result.add(summary(tickets, t));
            }
            return result;
        });
        http.json(200, list);
    }

    private void categories(Http http) throws Exception {
        requireTickets(http);
        List<Map<String, Object>> list = server.sync(() -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Settings.TicketCategory c : plugin.settings().tickets().categories()) {
                result.add(Map.of("id", c.id(), "name", plugin.tickets().categoryName(c.id()), "player", c.player()));
            }
            result.add(Map.of("id", TicketManager.LOCK_CATEGORY,
                    "name", plugin.tickets().categoryName(TicketManager.LOCK_CATEGORY), "player", false));
            return result;
        });
        http.json(200, list);
    }

    private void ticket(Http http) throws Exception {
        requireTickets(http);
        int id = id(http);
        Map<String, Object> result = server.sync(() -> {
            TicketManager tickets = plugin.tickets();
            Ticket t = tickets.get(id);
            if (t == null) {
                return null;
            }
            tickets.webMarkRead(http.account().id(), t);
            Map<String, Object> detail = summary(tickets, t);
            detail.put("world", t.world());
            detail.put("x", t.blockX());
            detail.put("y", t.blockY());
            detail.put("z", t.blockZ());
            detail.put("closedBy", t.closedBy());
            detail.put("closedAt", t.closedAt() == null ? 0 : t.closedAt().toEpochMilli());
            detail.put("mine", t.isCreator(http.account().id()));
            List<Map<String, Object>> messages = new ArrayList<>();
            for (Ticket.Message m : t.messages()) {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("uuid", m.author().toString());
                msg.put("name", m.authorName());
                msg.put("staff", m.staff());
                msg.put("system", TicketManager.SYSTEM.equals(m.author()));
                msg.put("text", m.text());
                msg.put("time", m.time().toEpochMilli());
                messages.add(msg);
            }
            detail.put("messages", messages);
            return detail;
        });
        if (result == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.json(200, result);
    }

    private void reply(Http http) throws Exception {
        requireTickets(http);
        require(http, Perms.TICKETS_REPLY);
        String text = http.string("text");
        if (text == null || text.length() > 4000) {
            throw new Http.WebException(400, "reply-empty");
        }
        act(http, "ticket-reply", (tickets, t) -> tickets.webReply(http.account().id(), http.account().name(), t, text),
                TicketText.preview(text, 120));
    }

    private void claim(Http http) throws Exception {
        requireTickets(http);
        require(http, Perms.TICKETS_CLAIM);
        act(http, "ticket-claim", (tickets, t) -> {
            tickets.webToggleClaim(http.account().id(), http.account().name(), t);
            return null;
        }, "");
    }

    private void close(Http http) throws Exception {
        requireTickets(http);
        require(http, Perms.TICKETS_CLOSE);
        String text = http.string("text");
        act(http, "ticket-close", (tickets, t) -> {
            if (text != null && !text.isBlank()) {
                String error = tickets.webReply(http.account().id(), http.account().name(), t, text);
                if (error != null) {
                    return error;
                }
            }
            return tickets.webClose(http.account().id(), http.account().name(), t);
        }, "");
    }

    private void reopen(Http http) throws Exception {
        requireTickets(http);
        require(http, Perms.TICKETS_REOPEN);
        act(http, "ticket-reopen", (tickets, t) -> tickets.webReopen(http.account().id(), http.account().name(), t), "");
    }

    private interface Action {
        String run(TicketManager tickets, Ticket ticket);
    }

    /** Runs a ticket action on the server thread; an error key becomes 400, success goes into the log. */
    private void act(Http http, String log, Action action, String detail) throws Exception {
        int id = id(http);
        String error = server.sync(() -> {
            Ticket t = plugin.tickets().get(id);
            return t == null ? "not-found" : action.run(plugin.tickets(), t);
        });
        if (error != null) {
            throw new Http.WebException(error.equals("not-found") ? 404 : 400, error);
        }
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.ip(), log, "#" + id + (detail.isEmpty() ? "" : " " + detail)));
    }

    private static int id(Http http) throws Http.WebException {
        try {
            return Integer.parseInt(http.param("id"));
        } catch (NumberFormatException e) {
            throw new Http.WebException(404, "not-found");
        }
    }

    /** What the list shows of a ticket. */
    private static Map<String, Object> summary(TicketManager tickets, Ticket t) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", t.id());
        s.put("category", t.category());
        s.put("categoryName", tickets.categoryName(t.category()));
        s.put("summary", tickets.summary(t));
        s.put("report", t.playerReport());
        s.put("creator", t.creatorName());
        s.put("creatorUuid", t.creator().toString());
        s.put("target", t.targetName());
        s.put("targetUuid", t.target() == null ? null : t.target().toString());
        s.put("open", t.open());
        s.put("claimedBy", t.claimedByName());
        s.put("unread", t.staffUnread());
        s.put("created", t.created().toEpochMilli());
        s.put("lastActivity", t.lastActivity().toEpochMilli());
        s.put("count", t.messages().size());
        s.put("source", t.source() == null ? null : t.source().toLowerCase(Locale.ROOT));
        Ticket.Message last = t.lastMessage();
        if (last != null) {
            s.put("last", Map.of("name", last.authorName(), "staff", last.staff(),
                    "text", TicketText.preview(last.text(), 120)));
        }
        return s;
    }
}
