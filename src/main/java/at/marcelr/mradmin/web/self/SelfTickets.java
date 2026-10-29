package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The player's own tickets on their page (/web) - the same as /report in game: open new ones, read and answer the
 * conversation, close own ones. Only tickets the player opened (also those a plugin opened for them, e.g. MRchunks for
 * a lag lock), never others. Needs the ticket feature and mradmin.tickets.create.
 * <pre>
 *  GET  /api/self/tickets                    categories they may choose and their tickets
 *  GET  /api/self/tickets/{id}               one ticket with its conversation (marks it read)
 *  POST /api/self/tickets      {category, target, subject, text}   a new ticket -> {id}
 *  POST /api/self/tickets/{id}/reply   {text}
 *  POST /api/self/tickets/{id}/close
 * </pre>
 * Refusals answer 400 {error, value} - value is the wait in ms, a maximum or a minimum length.
 */
public final class SelfTickets {

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes routes;

    SelfTickets(WebServer server, SelfRoutes routes) {
        this.server = server;
        this.plugin = server.plugin();
        this.routes = routes;
    }

    void register() {
        server.self("GET", "/api/self/tickets", this::list);
        server.self("GET", "/api/self/tickets/{id}", this::view);
        server.self("POST", "/api/self/tickets", this::create);
        server.self("POST", "/api/self/tickets/{id}/reply", this::reply);
        server.self("POST", "/api/self/tickets/{id}/close", this::close);
    }

    /** Tickets are on and the player may open them (also checked offline, with LuckPerms). */
    boolean available(UUID player) {
        return plugin.feature(Feature.TICKETS) && server.permissions().has(player, TicketManager.PERM_CREATE);
    }

    private void require(Http http) throws Exception {
        routes.check(http);
        if (!available(http.player().player())) {
            throw new Http.WebException(403, "no-self-permission");
        }
    }

    private List<Settings.TicketCategory> categories(UUID player) {
        List<Settings.TicketCategory> result = new ArrayList<>();
        for (Settings.TicketCategory c : plugin.settings().tickets().categories()) {
            if (c.permission().isEmpty() || server.permissions().has(player, c.permission())) {
                result.add(c);
            }
        }
        return result;
    }

    private void list(Http http) throws Exception {
        require(http);
        UUID player = http.player().player();
        List<Settings.TicketCategory> categories = categories(player);
        Map<String, Object> result = server.sync(() -> {
            TicketManager tickets = plugin.tickets();
            List<Map<String, Object>> cats = new ArrayList<>();
            for (Settings.TicketCategory c : categories) {
                cats.add(Map.of("id", c.id(), "name", tickets.categoryName(c.id()), "player", c.player()));
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (Ticket t : tickets.byCreator(player)) {
                if (!t.visibleInGame()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.id());
                m.put("summary", tickets.summary(t));
                m.put("category", tickets.categoryName(t.category()));
                m.put("open", t.open());
                m.put("unread", t.creatorUnread());
                m.put("lastActivity", t.lastActivity().toEpochMilli());
                m.put("fromPlugin", t.source() != null);
                list.add(m);
            }
            Settings.TicketSettings s = plugin.settings().tickets();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("categories", cats);
            r.put("tickets", list);
            r.put("minSubject", s.minSubjectLength());
            r.put("maxSubject", s.maxSubjectLength());
            r.put("minText", s.minTextLength());
            r.put("maxText", s.maxTextLength());
            return r;
        });
        http.json(200, result);
    }

    private Ticket own(Http http) throws Exception {
        int id;
        try {
            id = Integer.parseInt(http.param("id"));
        } catch (NumberFormatException e) {
            throw new Http.WebException(404, "not-found");
        }
        UUID player = http.player().player();
        Ticket ticket = server.sync(() -> plugin.tickets().get(id));
        if (ticket == null || !ticket.isCreator(player) || !ticket.visibleInGame()) {
            throw new Http.WebException(404, "not-found");
        }
        return ticket;
    }

    private void view(Http http) throws Exception {
        require(http);
        Ticket ticket = own(http);
        UUID player = http.player().player();
        Map<String, Object> result = server.sync(() -> {
            TicketManager tickets = plugin.tickets();
            tickets.webMarkRead(player, ticket);
            boolean names = plugin.settings().tickets().showStaffNames();
            List<Map<String, Object>> messages = new ArrayList<>();
            for (Ticket.Message msg : ticket.messages()) {
                Map<String, Object> m = new LinkedHashMap<>();
                boolean system = TicketManager.SYSTEM.equals(msg.author());
                m.put("own", player.equals(msg.author()));
                m.put("staff", msg.staff());
                m.put("system", system);
                // Team members' names only if tickets.show-staff-names - otherwise the page says "Team".
                m.put("author", system || !msg.staff() || names ? msg.authorName() : null);
                m.put("text", msg.text());
                m.put("time", msg.time().toEpochMilli());
                messages.add(m);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", ticket.id());
            r.put("summary", tickets.summary(ticket));
            r.put("category", tickets.categoryName(ticket.category()));
            r.put("open", ticket.open());
            r.put("mayClose", ticket.open() && !ticket.teamCloseOnly());
            r.put("messages", messages);
            return r;
        });
        http.json(200, result);
    }

    private void create(Http http) throws Exception {
        require(http);
        if (!server.allow("self-ticket:" + http.player().player(), 6)) {
            throw new Http.WebException(429, "rate-limit");
        }
        UUID player = http.player().player();
        String categoryId = http.string("category");
        Settings.TicketCategory category = categories(player).stream()
                .filter(c -> c.id().equals(categoryId)).findFirst().orElse(null);
        if (category == null) {
            throw new Http.WebException(400, "ticket-category");
        }
        String targetName = http.string("target");
        String subject = http.string("subject");
        String text = http.string("text");
        TicketManager.SelfResult result = server.sync(() -> {
            OfflinePlayer target = null;
            if (category.player()) {
                String name = targetName == null ? "" : targetName.strip();
                target = name.matches("[A-Za-z0-9_]{1,16}") ? Bukkit.getOfflinePlayerIfCached(name) : null;
                if (target == null) {
                    return new TicketManager.SelfResult(null, "ticket-target-unknown", 0);
                }
            }
            return plugin.tickets().selfCreate(player, http.player().name(), category, target,
                    subject == null ? "" : subject, text == null ? "" : text);
        });
        if (result.error() != null) {
            http.json(400, Map.of("error", result.error(), "value", result.value()));
            return;
        }
        http.json(200, Map.of("id", result.ticket().id()));
    }

    private void reply(Http http) throws Exception {
        require(http);
        Ticket ticket = own(http);
        String text = http.string("text");
        UUID player = http.player().player();
        String error = server.sync(() -> plugin.tickets().selfReply(player, http.player().name(), ticket,
                text == null ? "" : text));
        if (error != null) {
            http.json(400, Map.of("error", error, "value", plugin.settings().tickets().replyCooldown()));
        }
    }

    private void close(Http http) throws Exception {
        require(http);
        Ticket ticket = own(http);
        if (ticket.teamCloseOnly()) {
            throw new Http.WebException(403, "ticket-close-team-only");
        }
        UUID player = http.player().player();
        String error = server.sync(() -> plugin.tickets().webClose(player, http.player().name(), ticket));
        if (error != null) {
            throw new Http.WebException(400, error);
        }
    }
}
