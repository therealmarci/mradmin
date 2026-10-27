package at.marcelr.mradmin.ticket;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.util.TicketText;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Support tickets: players open them with /report (player reports, bugs, other requests) and talk to the team in
 * the ticket dialog. The team is notified, answers, claims, teleports and closes them.
 */
public final class TicketManager {

    /** Open tickets at all (/report); a category may additionally need its own permission. */
    public static final String PERM_CREATE = at.marcelr.mradmin.Perms.TICKETS_CREATE;
    public static final String PERM_NOTIFY = at.marcelr.mradmin.Perms.TICKETS_NOTIFY;

    /** Length of message previews in chat and item lore. */
    public static final int PREVIEW = 60;
    /** Author of the messages a plugin writes into a ticket (shown with that plugin's icon and name). */
    public static final UUID SYSTEM = new UUID(0L, 0L);
    /** Category of the tickets opened when MRchunks locks a chunk loader (not offered when creating tickets). */
    public static final String LOCK_CATEGORY = "chunkloader-lock";

    private final MRAdmin plugin;
    private final Map<Integer, Ticket> tickets = new HashMap<>();
    private final Map<UUID, Instant> lastTicket = new HashMap<>();
    private final Map<UUID, Instant> lastReply = new HashMap<>();
    private int nextId = 1;
    /** With a shared database: looks for changes of the other servers every few seconds. */
    private org.bukkit.scheduler.BukkitTask sync;
    private static final long SYNC_TICKS = 60;

    public TicketManager(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private Settings.TicketSettings settings() {
        return plugin.settings().tickets();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------ texts

    /**
     * Name of a category: the name from the config, otherwise the text from the language file
     * (ticket-categories.&lt;id&gt;), otherwise the id itself (also for categories removed from the config).
     */
    public String categoryName(String category) {
        if (category == null || category.isEmpty()) {
            return "-";
        }
        Settings.TicketCategory configured = settings().category(category);
        if (configured != null && !configured.name().isEmpty()) {
            return configured.name();
        }
        String key = "ticket-categories." + category;
        return messages().has(key) ? messages().plain(key) : category;
    }

    /** One line about what the ticket is: "Steve - Cheating" for player reports, otherwise the subject. */
    public String summary(Ticket ticket) {
        if (ticket.playerReport()) {
            return ticket.targetName() + " - " + categoryName(ticket.category());
        }
        return ticket.subject();
    }

    /** Name of a message's author as {@code viewer} sees it ("Team" instead of staff names if configured). */
    public String authorName(Ticket.Message message, Player viewer) {
        if (SYSTEM.equals(message.author())) {
            return message.authorName();
        }
        if (message.staff() && !settings().showStaffNames() && !viewer.hasPermission(Perms.TICKETS_VIEW)) {
            return messages().plain("ticket-staff-anonymous");
        }
        return message.authorName();
    }

    // ---------------------------------------------------------------- creating

    /** The categories {@code player} may choose, in config order. */
    public List<Settings.TicketCategory> categories(Player player) {
        if (!player.hasPermission(PERM_CREATE)) {
            return List.of();
        }
        return settings().categories().stream()
                .filter(c -> c.permission().isEmpty() || player.hasPermission(c.permission()))
                .toList();
    }

    /** Whether {@code player} may open tickets at all (no message). */
    public boolean mayCreate(Player player) {
        return !categories(player).isEmpty();
    }

    /**
     * Checks permission, cooldown and the number of open tickets before a form is shown and again when it is sent.
     * Tells the player what is wrong.
     */
    public boolean canStart(Player player) {
        Settings.TicketSettings s = settings();
        if (!mayCreate(player)) {
            messages().send(player, "no-permission");
            return false;
        }
        if (player.hasPermission(Perms.TICKETS_VIEW)) {
            return true;
        }
        Instant last = lastTicket.get(player.getUniqueId());
        if (last != null && Instant.now().isBefore(last.plusMillis(s.cooldown()))) {
            messages().send(player, "ticket-cooldown", tag("time",
                    Texts.duration(last.plusMillis(s.cooldown()).toEpochMilli() - System.currentTimeMillis())));
            return false;
        }
        long open = tickets.values().stream()
                .filter(t -> t.open() && t.isCreator(player.getUniqueId()) && !t.teamCloseOnly()).count();
        if (open >= s.maxOpenPerPlayer()) {
            messages().send(player, "ticket-too-many", tag("max", s.maxOpenPerPlayer()));
            return false;
        }
        return true;
    }

    /**
     * Opens a ticket from a form. Invalid input is reported to the player.
     *
     * @param target  reported player (categories with player: true), otherwise ignored
     * @param subject title (other categories), otherwise ignored
     * @return the new ticket, or null if something was wrong (the form should be shown again)
     */
    public Ticket create(Player creator, Settings.TicketCategory category, OfflinePlayer target, String subject,
                         String text) {
        Settings.TicketSettings s = settings();
        if (!canStart(creator)) {
            return null;
        }
        if (!category.permission().isEmpty() && !creator.hasPermission(category.permission())) {
            messages().send(creator, "no-permission");
            return null;
        }
        String cleanSubject = TicketText.limit(TicketText.clean(subject).replace('\n', ' '), s.maxSubjectLength());
        String cleanText = TicketText.limit(TicketText.clean(text), s.maxTextLength());
        if (category.player()) {
            if (target == null) {
                messages().send(creator, "ticket-no-target");
                return null;
            }
            if (target.getUniqueId().equals(creator.getUniqueId())) {
                messages().send(creator, "ticket-self");
                return null;
            }
            cleanSubject = "";
        } else {
            target = null;
            if (cleanSubject.length() < s.minSubjectLength()) {
                messages().send(creator, "ticket-subject-short", tag("min", s.minSubjectLength()));
                return null;
            }
        }
        if (cleanText.length() < Math.max(1, s.minTextLength())) {
            messages().send(creator, "ticket-text-short", tag("min", Math.max(1, s.minTextLength())));
            return null;
        }
        lastTicket.put(creator.getUniqueId(), Instant.now());

        // Player reports: where the reported player is right now (if online), otherwise where the creator stands.
        Player online = target == null ? null : target.getPlayer();
        Location where = online != null ? online.getLocation() : creator.getLocation();
        String targetName = target == null ? null : target.getName() == null ? target.getUniqueId().toString() : target.getName();
        Ticket ticket = new Ticket(newId(), creator.getUniqueId(), creator.getName(),
                target == null ? null : target.getUniqueId(), targetName, category.id(),
                cleanSubject, Instant.now(), where.getWorld().getName(), where.getX(), where.getY(), where.getZ());
        ticket.add(new Ticket.Message(creator.getUniqueId(), creator.getName(), false, cleanText, Instant.now()));
        tickets.put(ticket.id(), ticket);
        save(ticket);

        String categoryName = categoryName(category.id());
        messages().send(creator, "ticket-created", tag("id", ticket.id()), tag("type", categoryName),
                Messages.lazy("open", () -> openButton(ticket)));
        notifyNew(ticket);
        plugin.getLogger().info("Ticket #" + ticket.id() + " (" + category.id() + ") by " + creator.getName() + ": "
                + summary(ticket) + " - " + TicketText.preview(cleanText, 200));
        plugin.discord().log("ticket", "id", ticket.id(), "type", categoryName, "creator", creator.getName(),
                "summary", summary(ticket), "text", TicketText.preview(cleanText, 300),
                "world", ticket.world(), "x", ticket.blockX(), "y", ticket.blockY(), "z", ticket.blockZ());
        return ticket;
    }

    // ------------------------------------------------------- automatic tickets

    /** The open ticket a plugin opened about one of its objects (e.g. a chunk loader), or null. */
    public Ticket bySource(String source, String id) {
        return tickets.values().stream()
                .filter(t -> t.open() && source.equals(t.source()) && id.equals(t.sourceId()))
                .findFirst().orElse(null);
    }

    /**
     * A module reports a problem of a player (see {@link at.marcelr.mradmin.api.MRadminService#report}): a ticket for
     * the player with all details, in which they can explain what happened and which only the team can close - or,
     * if one is still open for the same key, the text as a note in it. The player is told in chat with a link.
     */
    public void report(String source, at.marcelr.mradmin.api.Report report) {
        if (report.kind().equals(LOCK_CATEGORY) && !settings().chunkloaderLocks()) {
            return;
        }
        Ticket open = bySource(source, report.key());
        String subject = TicketText.limit(report.subject(), 100);
        int id = open != null ? open.id() : newId();
        if (report.push() != null) {
            plugin.push(report.push(), "report", "/tickets/" + id, null,
                    "subject", subject, "player", report.playerName(), "plugin", source);
        }
        if (open != null) {
            note(open, source, messages().server(() -> messages().plain("ticket-report-again")) + "\n" + report.text(), true);
            notifyReport(open, report.player(), subject);
            notifyStaff(open, null, "ticket-notify-message", tag("id", open.id()), tag("player", source),
                    tag("text", TicketText.preview(report.text(), PREVIEW)), tag("open", openButton(open)));
            return;
        }
        var location = report.location();
        Ticket ticket = new Ticket(id, report.player(), report.playerName(), null, null, report.kind(), subject,
                Instant.now(), location == null || location.getWorld() == null ? "" : location.getWorld().getName(),
                location == null ? 0 : location.getX(), location == null ? 0 : location.getY(),
                location == null ? 0 : location.getZ());
        ticket.source(source, report.key());
        ticket.add(new Ticket.Message(SYSTEM, source, true, report.text(), Instant.now()));
        ticket.staffUnread(true);
        tickets.put(ticket.id(), ticket);
        save(ticket);

        notifyReport(ticket, report.player(), subject);
        notifyNew(ticket);
        plugin.getLogger().info("Ticket #" + ticket.id() + " (" + report.kind() + ") for " + ticket.creatorName()
                + ": " + subject + " (opened by " + source + ").");
        plugin.discord().log("ticket", "id", ticket.id(), "type", categoryName(report.kind()), "creator", ticket.creatorName(),
                "summary", subject, "text", TicketText.preview(report.text(), 300),
                "world", ticket.world(), "x", ticket.blockX(), "y", ticket.blockY(), "z", ticket.blockZ());
    }

    /** The problem behind {@code key} is solved: the note goes into the open ticket (if there is one). */
    public void resolve(String source, String key, String note) {
        Ticket open = bySource(source, key);
        if (open != null) {
            note(open, source, note, false);
        }
    }

    // ------------------------------------------------------ public web forms

    /** "Creator" of contact requests from the imprint - nobody in game. */
    public static final UUID GUEST = new UUID(0L, 1L);

    /**
     * A ticket from the public web forms (unban request or contact). The first message is what the person wrote;
     * {@code note} (may be null) follows as a message of MRadmin, e.g. the ban details. Contact requests are announced
     * only in the web (push), unban requests also to the team in game. Server thread only.
     */
    public Ticket createRequest(WebRequest request, UUID creator, String creatorName, String subject, String text,
                                String note) {
        Ticket ticket = new Ticket(newId(), creator, creatorName, null, null, request.kind(), subject, Instant.now(),
                "", 0, 0, 0);
        ticket.request(request);
        ticket.add(new Ticket.Message(creator, creatorName, false, text, Instant.now()));
        if (note != null) {
            ticket.add(new Ticket.Message(SYSTEM, "MRadmin", true, note, Instant.now()));
        }
        ticket.creatorUnread(false);
        ticket.staffUnread(true);
        tickets.put(ticket.id(), ticket);
        save(ticket);
        notifyNew(ticket);
        plugin.getLogger().info("Ticket #" + ticket.id() + " (" + request.kind() + ", web) by " + creatorName + ": "
                + subject);
        plugin.discord().log("ticket", "id", ticket.id(), "type", categoryName(request.kind()), "creator", creatorName,
                "summary", subject, "text", TicketText.preview(text, 300), "world", "-", "x", 0, "y", 0, "z", 0);
        return ticket;
    }

    /** The person behind a web request answered through their status link. @return error key or null */
    public String requesterReply(Ticket ticket, String text) {
        if (!ticket.open()) {
            return "ticket-closed";
        }
        ticket.add(new Ticket.Message(ticket.creator(), ticket.creatorName(), false, text, Instant.now()));
        ticket.creatorUnread(false);
        save(ticket);
        if (ticket.visibleInGame()) {
            notifyStaff(ticket, null, "ticket-notify-message", tag("id", ticket.id()), tag("player", ticket.creatorName()),
                    tag("text", TicketText.preview(text, PREVIEW)), Messages.lazy("open", () -> openButton(ticket)));
        }
        pushMessage(ticket, ticket.creatorName(), text, null);
        plugin.discord().log("ticket-message", "id", ticket.id(), "author", ticket.creatorName(),
                "text", TicketText.preview(text, 300), "summary", messages().server(() -> summary(ticket)));
        return null;
    }

    /**
     * The one appeal after a rejected unban request: the ticket opens again as appeal, MRadmin notes it, the appeal
     * text follows. The caller has checked that it is allowed.
     */
    public void appeal(Ticket ticket, String text) {
        ticket.request(ticket.request().asAppeal());
        ticket.reopen();
        ticket.add(new Ticket.Message(SYSTEM, "MRadmin", true, messages().server(() -> messages().plain("request-appeal-note")), Instant.now()));
        ticket.add(new Ticket.Message(ticket.creator(), ticket.creatorName(), false, text, Instant.now()));
        ticket.creatorUnread(false);
        ticket.staffUnread(true);
        save(ticket);
        notifyNew(ticket);
        plugin.discord().log("ticket-reopened", "id", ticket.id(), "admin", ticket.creatorName(), "summary", messages().server(() -> summary(ticket)));
    }

    /**
     * Decides an unban request (accepted: the caller has lifted the ban already) and closes it; {@code text} (may be
     * empty) goes to the person first. @return error key or null
     */
    public String decide(Ticket ticket, boolean accepted, UUID who, String name, String text) {
        if (ticket.request() == null || !ticket.request().unban()) {
            return "not-unban";
        }
        if (!ticket.open()) {
            return "ticket-closed";
        }
        String clean = text == null ? "" : TicketText.limit(TicketText.clean(text), settings().maxTextLength());
        if (!clean.isEmpty()) {
            ticket.add(new Ticket.Message(who, name, true, clean, Instant.now()));
        }
        ticket.request(ticket.request().withDecision(accepted ? WebRequest.ACCEPTED : WebRequest.REJECTED));
        ticket.add(new Ticket.Message(SYSTEM, "MRadmin", true, messages().server(() -> messages().plain(accepted ? "request-accepted-note"
                : "request-rejected-note", "admin", name)), Instant.now()));
        ticket.close(name, Instant.now());
        ticket.creatorUnread(true);
        ticket.staffUnread(false);
        save(ticket);
        plugin.discord().log("ticket-closed", "id", ticket.id(), "admin", name, "summary", messages().server(() -> summary(ticket)));
        // One mail with the decision and the answer.
        requestChanged(ticket, clean);
        return null;
    }

    /** The ticket of a status link, or null. */
    public Ticket byToken(String token) {
        return tickets.values().stream()
                .filter(t -> t.request() != null && token.equals(t.request().token()))
                .findFirst().orElse(null);
    }

    /** The unban request about this ban (open or closed, while it is kept), or null. */
    public Ticket unbanRequest(long banId) {
        return tickets.values().stream()
                .filter(t -> t.request() != null && t.request().unban() && t.request().banId() == banId)
                .findFirst().orElse(null);
    }

    /** Open contact requests (the form takes no new ones above web.requests.spam.max-open-contact). */
    public int openContactRequests() {
        return (int) tickets.values().stream().filter(t -> t.open() && !t.visibleInGame()).count();
    }

    /** Tells MRadmin's request service about a team answer or closing (mail to the person, request bookkeeping). */
    private void requestChanged(Ticket ticket, String answer) {
        if (ticket.request() == null || plugin.web() == null) {
            return;
        }
        if (!ticket.open() && ticket.request().unban() && ticket.request().decision() == null) {
            // Closed without a decision (in game or plain close in the web): the ban stays, so it counts as rejected.
            ticket.request(ticket.request().withDecision(WebRequest.REJECTED));
            save(ticket);
        }
        plugin.web().requests().changed(ticket, answer);
    }

    /** A message of the plugin itself in the ticket (the creator sees it as unread; the team too if asked). */
    private void note(Ticket ticket, String source, String text, boolean forStaff) {
        ticket.add(new Ticket.Message(SYSTEM, source, true, text, Instant.now()));
        if (forStaff) {
            ticket.staffUnread(true);
        }
        save(ticket);
    }

    /** Tells the player (if online) that the ticket is there and that they can explain themselves in it. */
    private void notifyReport(Ticket ticket, UUID player, String subject) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            messages().send(online, "ticket-report-notify", tag("id", ticket.id()), tag("subject", subject),
                    Messages.lazy("open", () -> openButton(ticket)));
            sound(online);
        }
    }

    // ---------------------------------------------------------------- handling

    /** The creator and the team may open a ticket. */
    public boolean canView(Player player, Ticket ticket) {
        return ticket.isCreator(player.getUniqueId()) || player.hasPermission(Perms.TICKETS_VIEW);
    }

    /** Whether {@code player} writes as team member in this ticket (team, but not the creator). */
    public boolean asStaff(Player player, Ticket ticket) {
        return !ticket.isCreator(player.getUniqueId()) && player.hasPermission(Perms.TICKETS_VIEW);
    }

    /**
     * Adds a message to the conversation and tells the other side.
     *
     * @return false if it was not sent (too short, cooldown, closed ...); the player was told why
     */
    public boolean reply(Player author, Ticket ticket, String text) {
        Settings.TicketSettings s = settings();
        if (!canView(author, ticket)) {
            messages().send(author, "no-permission");
            return false;
        }
        if (!ticket.open()) {
            messages().send(author, "ticket-is-closed", tag("id", ticket.id()));
            return false;
        }
        String clean = TicketText.limit(TicketText.clean(text), s.maxTextLength());
        if (clean.isEmpty()) {
            messages().send(author, "ticket-reply-empty");
            return false;
        }
        Instant last = lastReply.get(author.getUniqueId());
        if (last != null && !author.hasPermission(Perms.TICKETS_VIEW) && Instant.now().isBefore(last.plusMillis(s.replyCooldown()))) {
            messages().send(author, "ticket-reply-cooldown", tag("time",
                    Texts.duration(last.plusMillis(s.replyCooldown()).toEpochMilli() - System.currentTimeMillis())));
            return false;
        }
        boolean staff = asStaff(author, ticket);
        if (staff && !author.hasPermission(Perms.TICKETS_REPLY)) {
            messages().send(author, "no-permission");
            return false;
        }
        lastReply.put(author.getUniqueId(), Instant.now());
        ticket.add(new Ticket.Message(author.getUniqueId(), author.getName(), staff, clean, Instant.now()));
        if (staff) {
            ticket.staffUnread(false);
            if (s.autoClaim() && ticket.claimedBy() == null) {
                ticket.claim(author.getUniqueId(), author.getName());
            }
        } else {
            ticket.creatorUnread(false);
        }
        save(ticket);

        if (staff) {
            Player creator = Bukkit.getPlayer(ticket.creator());
            if (creator != null) {
                messages().send(creator, "ticket-answer", tag("id", ticket.id()), Messages.lazy("open", () -> openButton(ticket)),
                        tag("text", TicketText.preview(clean, PREVIEW)));
                sound(creator);
            }
        } else {
            notifyStaff(ticket, author, "ticket-notify-message", tag("id", ticket.id()), tag("player", author.getName()),
                    tag("text", TicketText.preview(clean, PREVIEW)), Messages.lazy("open", () -> openButton(ticket)));
            pushMessage(ticket, author.getName(), clean, author.getUniqueId());
        }
        plugin.discord().log("ticket-message", "id", ticket.id(), "author", author.getName(),
                "text", TicketText.preview(clean, 300), "summary", messages().server(() -> summary(ticket)));
        if (staff) {
            requestChanged(ticket, clean);
        }
        return true;
    }

    /** Closes the ticket (creator or team). The other side is told about it. */
    public void close(Player by, Ticket ticket) {
        if (!canView(by, ticket)) {
            messages().send(by, "no-permission");
            return;
        }
        if (!ticket.open()) {
            return;
        }
        if (ticket.teamCloseOnly() && !by.hasPermission(Perms.TICKETS_CLOSE)) {
            messages().send(by, "ticket-close-team-only");
            return;
        }
        if (!ticket.isCreator(by.getUniqueId()) && !by.hasPermission(Perms.TICKETS_CLOSE)) {
            messages().send(by, "no-permission");
            return;
        }
        ticket.close(by.getName(), Instant.now());
        boolean staff = asStaff(by, ticket);
        if (staff) {
            ticket.creatorUnread(true);
            ticket.staffUnread(false);
        } else {
            ticket.creatorUnread(false);
        }
        save(ticket);
        messages().send(by, "ticket-closed", tag("id", ticket.id()));
        if (staff) {
            Player creator = Bukkit.getPlayer(ticket.creator());
            if (creator != null && settings().notifyCreator()) {
                messages().send(creator, "ticket-closed-creator", tag("id", ticket.id()),
                        Messages.lazy("summary", () -> summary(ticket)), Messages.lazy("open", () -> openButton(ticket)));
                sound(creator);
            }
        } else if (ticket.claimedBy() != null) {
            Player claimer = Bukkit.getPlayer(ticket.claimedBy());
            if (claimer != null && !claimer.equals(by)) {
                messages().send(claimer, "ticket-closed-by-creator", tag("id", ticket.id()), tag("player", by.getName()));
            }
        }
        plugin.discord().log("ticket-closed", "id", ticket.id(), "admin", by.getName(), "summary", messages().server(() -> summary(ticket)));
        requestChanged(ticket, null);
    }

    /** Opens a closed ticket again (team only). */
    public void reopen(Player staff, Ticket ticket) {
        if (!staff.hasPermission(Perms.TICKETS_REOPEN)) {
            messages().send(staff, "no-permission");
            return;
        }
        if (ticket.open()) {
            return;
        }
        ticket.reopen();
        ticket.creatorUnread(!ticket.isCreator(staff.getUniqueId()));
        save(ticket);
        messages().send(staff, "ticket-reopened", tag("id", ticket.id()));
        Player creator = Bukkit.getPlayer(ticket.creator());
        if (creator != null && !creator.equals(staff)) {
            messages().send(creator, "ticket-reopened-creator", tag("id", ticket.id()), Messages.lazy("open", () -> openButton(ticket)));
        }
        plugin.discord().log("ticket-reopened", "id", ticket.id(), "admin", staff.getName(), "summary", messages().server(() -> summary(ticket)));
    }

    /** Claims the ticket for {@code staff}, or releases it if they already have it. */
    public void toggleClaim(Player staff, Ticket ticket) {
        if (!staff.hasPermission(Perms.TICKETS_CLAIM)) {
            messages().send(staff, "no-permission");
            return;
        }
        if (staff.getUniqueId().equals(ticket.claimedBy())) {
            ticket.claim(null, null);
            messages().send(staff, "ticket-released", tag("id", ticket.id()));
        } else {
            String before = ticket.claimedByName();
            ticket.claim(staff.getUniqueId(), staff.getName());
            messages().send(staff, before == null ? "ticket-claimed" : "ticket-claimed-from",
                    tag("id", ticket.id()), tag("player", before == null ? "" : before));
        }
        save(ticket);
    }

    // -------------------------------------------------------------------- web

    // The web interface is for the team; its users are usually not online. These do what the in-game actions do
    // (same notices to the creator, Discord log, auto-claim) for a member given by UUID and name. Server thread only.
    // Each returns an error key for the web interface, or null if it worked.

    public String webReply(UUID who, String name, Ticket ticket, String text) {
        if (!ticket.open()) {
            return "ticket-closed";
        }
        String clean = TicketText.limit(TicketText.clean(text), settings().maxTextLength());
        if (clean.isEmpty()) {
            return "reply-empty";
        }
        boolean staff = !ticket.isCreator(who);
        ticket.add(new Ticket.Message(who, name, staff, clean, Instant.now()));
        if (staff) {
            ticket.staffUnread(false);
            if (settings().autoClaim() && ticket.claimedBy() == null) {
                ticket.claim(who, name);
            }
        } else {
            ticket.creatorUnread(false);
        }
        save(ticket);
        if (staff) {
            Player creator = Bukkit.getPlayer(ticket.creator());
            if (creator != null) {
                messages().send(creator, "ticket-answer", tag("id", ticket.id()), Messages.lazy("open", () -> openButton(ticket)),
                        tag("text", TicketText.preview(clean, PREVIEW)));
                sound(creator);
            }
        } else {
            notifyStaff(ticket, null, "ticket-notify-message", tag("id", ticket.id()), tag("player", name),
                    tag("text", TicketText.preview(clean, PREVIEW)), Messages.lazy("open", () -> openButton(ticket)));
            pushMessage(ticket, name, clean, ticket.creator());
        }
        plugin.discord().log("ticket-message", "id", ticket.id(), "author", name,
                "text", TicketText.preview(clean, 300), "summary", messages().server(() -> summary(ticket)));
        if (staff) {
            requestChanged(ticket, clean);
        }
        return null;
    }

    public String webClose(UUID who, String name, Ticket ticket) {
        if (!ticket.open()) {
            return "ticket-closed";
        }
        ticket.close(name, Instant.now());
        ticket.creatorUnread(!ticket.isCreator(who));
        ticket.staffUnread(false);
        save(ticket);
        Player creator = Bukkit.getPlayer(ticket.creator());
        if (creator != null && !ticket.isCreator(who) && settings().notifyCreator()) {
            messages().send(creator, "ticket-closed-creator", tag("id", ticket.id()),
                    Messages.lazy("summary", () -> summary(ticket)), Messages.lazy("open", () -> openButton(ticket)));
            sound(creator);
        }
        requestChanged(ticket, null);
        plugin.discord().log("ticket-closed", "id", ticket.id(), "admin", name, "summary", messages().server(() -> summary(ticket)));
        return null;
    }

    public String webReopen(UUID who, String name, Ticket ticket) {
        if (ticket.open()) {
            return "ticket-open";
        }
        ticket.reopen();
        ticket.creatorUnread(!ticket.isCreator(who));
        save(ticket);
        Player creator = Bukkit.getPlayer(ticket.creator());
        if (creator != null && !ticket.isCreator(who)) {
            messages().send(creator, "ticket-reopened-creator", tag("id", ticket.id()), Messages.lazy("open", () -> openButton(ticket)));
        }
        plugin.discord().log("ticket-reopened", "id", ticket.id(), "admin", name, "summary", messages().server(() -> summary(ticket)));
        return null;
    }

    /** Claims the ticket for the member, or releases it if they already have it. */
    public void webToggleClaim(UUID who, String name, Ticket ticket) {
        ticket.claim(who.equals(ticket.claimedBy()) ? null : who, name);
        save(ticket);
    }

    /** The member looked at the ticket in the web: no longer unread for the team (or the creator). */
    public void webMarkRead(UUID who, Ticket ticket) {
        boolean creator = ticket.isCreator(who);
        if (creator ? ticket.creatorUnread() : ticket.staffUnread()) {
            if (creator) {
                ticket.creatorUnread(false);
            } else {
                ticket.staffUnread(false);
            }
            save(ticket);
        }
    }

    /** Teleports staff to the reported player (if online) or to where the ticket was opened. */
    public void teleport(Player staff, Ticket ticket) {
        if (!staff.hasPermission(Perms.TICKETS_TELEPORT)) {
            messages().send(staff, "no-permission");
            return;
        }
        if (!plugin.feature(staff, at.marcelr.mradmin.Feature.TELEPORT)) {
            return;
        }
        Player target = ticket.target() == null ? null : Bukkit.getPlayer(ticket.target());
        Location to = target != null ? target.getLocation() : ticket.location();
        if (to != null && target == null && ticket.source() != null) {
            // Tickets of a plugin point at a block (e.g. the chunk loader) - stand on it, not in it.
            to = to.clone().add(0, 1, 0);
        }
        if (to == null) {
            messages().send(staff, "ticket-no-location");
            return;
        }
        staff.teleport(to);
        messages().send(staff, target != null ? "ticket-teleported-player" : "ticket-teleported-place",
                tag("id", ticket.id()), tag("player", ticket.targetName() == null ? "" : ticket.targetName()));
    }

    /** The viewer has seen the latest state: clears the unread marker of their side. */
    public void markRead(Player viewer, Ticket ticket) {
        boolean changed = false;
        if (ticket.isCreator(viewer.getUniqueId()) && ticket.creatorUnread()) {
            ticket.creatorUnread(false);
            changed = true;
        }
        if (asStaff(viewer, ticket) && ticket.staffUnread()) {
            ticket.staffUnread(false);
            changed = true;
        }
        if (changed) {
            save(ticket);
        }
    }

    // ----------------------------------------------------------- notifications

    /** Clickable "[Öffnen]" that opens the ticket dialog (/report #id works for the creator and the team). */
    public Component openButton(Ticket ticket) {
        return messages().get("ticket-button-open")
                .clickEvent(ClickEvent.runCommand("/report #" + ticket.id()))
                .hoverEvent(HoverEvent.showText(messages().get("ticket-button-open-hover", tag("id", ticket.id()))));
    }

    private Component teleportButton(Ticket ticket) {
        if (!plugin.feature(at.marcelr.mradmin.Feature.TELEPORT)) {
            return Component.empty();
        }
        return messages().get("ticket-button-teleport")
                .clickEvent(ClickEvent.runCommand("/mradmin tickets tp " + ticket.id()))
                .hoverEvent(HoverEvent.showText(messages().get("ticket-button-teleport-hover")));
    }

    private Component listButton() {
        return messages().get("ticket-button-list")
                .clickEvent(ClickEvent.runCommand("/mradmin tickets"))
                .hoverEvent(HoverEvent.showText(messages().get("ticket-button-list-hover")));
    }

    private void notifyNew(Ticket ticket) {
        for (Player staff : ticket.visibleInGame() ? Bukkit.getOnlinePlayers() : List.<Player>of()) {
            if (staff.hasPermission(PERM_NOTIFY) && !ticket.isCreator(staff.getUniqueId())) {
                messages().send(staff, "ticket-notify-new", tag("id", ticket.id()), Messages.lazy("type", () -> categoryName(ticket.category())),
                        tag("player", ticket.creatorName()), Messages.lazy("summary", () -> summary(ticket)),
                        tag("text", TicketText.preview(ticket.description(), PREVIEW)),
                        Messages.lazy("open", () -> openButton(ticket)), Messages.lazy("teleport", () -> teleportButton(ticket)), Messages.lazy("list", this::listButton));
                sound(staff);
            }
        }
        // Push to the team's devices - not for lock tickets, those come as "chunk loader locked".
        if (ticket.source() == null) {
            plugin.push("ticket-new", ticket.visibleInGame() ? "ticket-new" : "ticket-contact", "/tickets/" + ticket.id(),
                    id -> !ticket.isCreator(id),
                    "id", ticket.id(), "player", ticket.creatorName(), "summary", messages().server(() -> summary(ticket)));
        }
    }

    /**
     * Push for a new message from the player: the one who claimed the ticket gets "assigned to me", everyone else
     * (who wants it) "any ticket message". The author never gets their own message.
     */
    private void pushMessage(Ticket ticket, String author, String text, UUID except) {
        String url = "/tickets/" + ticket.id();
        Object[] values = {"id", ticket.id(), "player", author, "text", TicketText.preview(text, 200)};
        UUID claimer = ticket.claimedBy();
        if (claimer != null) {
            plugin.push("ticket-mine", "ticket-message", url, id -> id.equals(claimer) && !id.equals(except), values);
        }
        plugin.push("ticket-message", "ticket-message", url, id -> !id.equals(claimer) && !id.equals(except), values);
    }

    /** To the member who claimed the ticket if they are online, otherwise to the whole team on duty. */
    private void notifyStaff(Ticket ticket, Player except, String key, TagResolver... resolvers) {
        Player claimer = ticket.claimedBy() == null ? null : Bukkit.getPlayer(ticket.claimedBy());
        List<Player> to = claimer != null ? List.of(claimer) : Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(PERM_NOTIFY)).map(p -> (Player) p).toList();
        for (Player staff : to) {
            if (!staff.equals(except)) {
                messages().send(staff, key, resolvers);
                sound(staff);
            }
        }
    }

    private void sound(Player player) {
        Key sound = settings().sound();
        if (sound != null) {
            player.playSound(Sound.sound(sound, Sound.Source.MASTER, 0.8f, 1.2f));
        }
    }

    /** On join: the team learns about open tickets, players about answers they have not read yet. */
    public void notifyOnJoin(Player player) {
        messages().run(player, () -> notifyOnJoinNow(player));
    }

    private void notifyOnJoinNow(Player player) {
        int unread = (int) tickets.values().stream().filter(t -> t.isCreator(player.getUniqueId()) && t.creatorUnread()).count();
        if (unread > 0) {
            messages().send(player, "ticket-join-unread", tag("count", unread), tag("list", messages().get("ticket-button-mine")
                    .clickEvent(ClickEvent.runCommand("/report"))
                    .hoverEvent(HoverEvent.showText(messages().get("ticket-button-mine-hover")))));
        }
        int open = openCountInGame();
        if (open > 0 && player.hasPermission(PERM_NOTIFY)) {
            messages().send(player, "ticket-join-staff", tag("count", open), tag("unread", staffUnreadCountInGame()),
                    Messages.lazy("list", this::listButton));
        }
    }

    // ------------------------------------------------------------------ access

    public Ticket get(int id) {
        return tickets.get(id);
    }

    /** Tickets of a creator: open ones first, each group newest activity first. */
    public List<Ticket> byCreator(UUID creator) {
        return tickets.values().stream()
                .filter(t -> t.isCreator(creator))
                .sorted(Comparator.comparing(Ticket::open).reversed()
                        .thenComparing(Comparator.comparing(Ticket::lastActivity).reversed()))
                .toList();
    }

    /**
     * Tickets for the team menu, newest activity first; unread ones first among open tickets.
     *
     * @param category category id, null = all categories
     * @param about    only player reports about this player, null = all
     */
    public List<Ticket> list(String category, boolean open, UUID about) {
        Comparator<Ticket> order = Comparator.comparing(Ticket::lastActivity).reversed();
        if (open) {
            order = Comparator.comparing((Ticket t) -> t.staffUnread()).reversed().thenComparing(order);
        }
        return tickets.values().stream()
                .filter(t -> t.open() == open)
                .filter(t -> category == null || category.equals(t.category()))
                .filter(t -> about == null || about.equals(t.target()))
                .sorted(order)
                .toList();
    }

    public int openCount() {
        return (int) tickets.values().stream().filter(Ticket::open).count();
    }

    /** Like {@link #list}, without the contact requests that exist only in the web interface. */
    public List<Ticket> listInGame(String category, boolean open, UUID about) {
        return list(category, open, about).stream().filter(Ticket::visibleInGame).toList();
    }

    public int openCountInGame() {
        return (int) tickets.values().stream().filter(t -> t.open() && t.visibleInGame()).count();
    }

    public int staffUnreadCountInGame() {
        return (int) tickets.values().stream().filter(t -> t.open() && t.staffUnread() && t.visibleInGame()).count();
    }

    /** A ticket by id for in-game commands - contact requests do not exist there. */
    public Ticket getInGame(int id) {
        Ticket ticket = tickets.get(id);
        return ticket == null || !ticket.visibleInGame() ? null : ticket;
    }

    /** Open tickets per team member who claimed them. */
    public Map<UUID, Integer> openClaimedCounts() {
        Map<UUID, Integer> counts = new HashMap<>();
        for (Ticket t : tickets.values()) {
            if (t.open() && t.claimedBy() != null) {
                counts.merge(t.claimedBy(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Open tickets with a message from the creator the team has not read. */
    public int staffUnreadCount() {
        return (int) tickets.values().stream().filter(t -> t.open() && t.staffUnread()).count();
    }

    /** Open player tickets about {@code target}. */
    public int openAbout(UUID target) {
        return (int) tickets.values().stream().filter(t -> t.open() && target.equals(t.target())).count();
    }

    // ------------------------------------------------------------- persistence

    /** Loads all tickets from the storage (YAML, SQLite or MySQL); takes over reports.yml of version 0.1.0. */
    public void load() {
        if (sync != null) {
            sync.cancel();
            sync = null;
        }
        tickets.clear();
        Storage.Tickets loaded = plugin.storage().loadTickets();
        nextId = loaded.nextId();
        for (Storage.LoadedTicket t : loaded.tickets()) {
            Ticket ticket = fromRow(t.ticket(), t.messages());
            tickets.put(ticket.id(), ticket);
        }
        File old = new File(plugin.getDataFolder(), "reports.yml");
        if (tickets.isEmpty() && old.exists()) {
            migrate(old);
        }
        cleanup();
        if (plugin.storage().shared()) {
            // Tickets of the other servers using the same database (e.g. the waiting lobby behind a proxy).
            sync = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                long asked = System.currentTimeMillis();
                plugin.storage().pollTickets(changed -> {
                    if (plugin.isEnabled() && !changed.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> merge(changed, asked));
                    }
                });
            }, SYNC_TICKS, SYNC_TICKS);
        }
    }

    /** The next ticket number - from the database if several servers share it. */
    private int newId() {
        return plugin.storage().shared() ? plugin.storage().nextTicketId() : nextId++;
    }

    /**
     * Takes over what other servers changed in the shared database: new tickets, new messages, claim, close/reopen,
     * read markers. Players and the team on this server get the same notices as if it had happened here (Discord and
     * the log are done by the server where it happened; push and request mails by the server with the web interface).
     * A ticket this server changed after the poll started is left alone - it comes again with the next poll.
     */
    private void merge(List<Storage.LoadedTicket> changed, long asked) {
        for (Storage.LoadedTicket loaded : changed) {
            Storage.TicketRow row = loaded.ticket();
            Ticket ticket = tickets.get(row.id());
            if (ticket == null) {
                ticket = fromRow(row, loaded.messages());
                if (!ticket.open() && ticket.closedAt() != null
                        && ticket.closedAt().isBefore(Instant.now().minusMillis(settings().keepClosed()))) {
                    continue;
                }
                tickets.put(ticket.id(), ticket);
                webChanged(ticket);
                if (ticket.open()) {
                    plugin.getLogger().info("Ticket #" + ticket.id() + " (" + ticket.category() + ") by "
                            + ticket.creatorName() + " was opened on another server: " + summary(ticket));
                    notifyNew(ticket);
                }
                continue;
            }
            if (ticket.changedHere() >= asked) {
                continue;
            }
            boolean wasOpen = ticket.open();
            UUID claimedBefore = ticket.claimedBy();
            java.util.Set<String> have = new java.util.HashSet<>();
            for (Ticket.Message m : ticket.messages()) {
                have.add(key(m.author(), m.time().toEpochMilli(), m.text()));
            }
            List<Ticket.Message> added = new java.util.ArrayList<>();
            for (Storage.MessageRow m : loaded.messages()) {
                if (have.add(key(m.author(), m.time(), m.text()))) {
                    Ticket.Message message = new Ticket.Message(m.author(), m.authorName(), m.staff(), m.text(),
                            Instant.ofEpochMilli(m.time()));
                    ticket.addFromStorage(message);
                    added.add(message);
                }
            }
            boolean fields = !java.util.Objects.equals(row.claimedBy(), ticket.claimedBy())
                    || !java.util.Objects.equals(row.closedBy(), ticket.closedBy())
                    || row.creatorUnread() != ticket.creatorUnread() || row.staffUnread() != ticket.staffUnread()
                    || !java.util.Objects.equals(row.extra(), ticket.request() == null ? null : ticket.request().json());
            if (!fields && added.isEmpty()) {
                continue;
            }
            ticket.claim(row.claimedBy(), row.claimedByName());
            if (row.closedBy() == null) {
                if (!ticket.open()) {
                    ticket.reopen();
                }
            } else {
                ticket.close(row.closedBy(), Instant.ofEpochMilli(row.closedAt()));
            }
            ticket.creatorUnread(row.creatorUnread());
            ticket.staffUnread(row.staffUnread());
            ticket.request(WebRequest.parse(row.extra()));
            if (row.source() != null) {
                ticket.source(row.source(), row.sourceId());
            }
            webChanged(ticket);
            notifyRemote(ticket, added, wasOpen, claimedBefore);
        }
    }

    private static String key(UUID author, long time, String text) {
        return author + "|" + time + "|" + text;
    }

    /** Notices for what happened on another server - like {@link #reply}, {@link #close} and {@link #reopen} do. */
    private void notifyRemote(Ticket ticket, List<Ticket.Message> added, boolean wasOpen, UUID claimedBefore) {
        Player creator = Bukkit.getPlayer(ticket.creator());
        for (Ticket.Message m : added) {
            if (m.staff()) {
                if (creator != null && !creator.getUniqueId().equals(m.author())) {
                    Ticket t = ticket;
                    messages().send(creator, "ticket-answer", tag("id", t.id()), Messages.lazy("open", () -> openButton(t)),
                            tag("text", TicketText.preview(m.text(), PREVIEW)));
                    sound(creator);
                }
            } else {
                Ticket t = ticket;
                notifyStaff(ticket, Bukkit.getPlayer(m.author()), "ticket-notify-message", tag("id", t.id()),
                        tag("player", m.authorName()), tag("text", TicketText.preview(m.text(), PREVIEW)),
                        Messages.lazy("open", () -> openButton(t)));
                pushMessage(ticket, m.authorName(), m.text(), m.author());
            }
            if (m.staff() && plugin.web() != null && ticket.request() != null) {
                plugin.web().requests().changed(ticket, m.text());
            }
        }
        Ticket t = ticket;
        if (wasOpen && !ticket.open()) {
            if (creator != null && settings().notifyCreator() && !ticket.creatorName().equals(ticket.closedBy())) {
                messages().send(creator, "ticket-closed-creator", tag("id", t.id()),
                        Messages.lazy("summary", () -> summary(t)), Messages.lazy("open", () -> openButton(t)));
                sound(creator);
            }
            if (plugin.web() != null && ticket.request() != null) {
                plugin.web().requests().changed(ticket, null);
            }
        } else if (!wasOpen && ticket.open() && creator != null) {
            messages().send(creator, "ticket-reopened-creator", tag("id", t.id()), Messages.lazy("open", () -> openButton(t)));
        }
    }

    private void webChanged(Ticket ticket) {
        if (plugin.web() != null) {
            plugin.web().events().ticket(ticket.id());
        }
    }

    private static Ticket fromRow(Storage.TicketRow r, List<Storage.MessageRow> messages) {
        Ticket ticket = new Ticket(r.id(), r.creator(), r.creatorName(), r.target(), r.targetName(), r.category(),
                r.subject(), Instant.ofEpochMilli(r.created()), r.world(), r.x(), r.y(), r.z());
        for (Storage.MessageRow m : messages) {
            ticket.add(new Ticket.Message(m.author(), m.authorName(), m.staff(), m.text(), Instant.ofEpochMilli(m.time())));
        }
        if (r.claimedBy() != null) {
            ticket.claim(r.claimedBy(), r.claimedByName());
        }
        if (r.closedBy() != null) {
            ticket.close(r.closedBy(), Instant.ofEpochMilli(r.closedAt()));
        }
        ticket.creatorUnread(r.creatorUnread());
        ticket.staffUnread(r.staffUnread());
        if (r.source() != null) {
            ticket.source(r.source(), r.sourceId());
        }
        ticket.request(WebRequest.parse(r.extra()));
        ticket.markSaved();
        return ticket;
    }

    private static Storage.TicketRow row(Ticket t) {
        return new Storage.TicketRow(t.id(), t.creator(), t.creatorName(), t.target(), t.targetName(), t.category(),
                t.subject(), t.created().toEpochMilli(), t.world(), t.x(), t.y(), t.z(), t.claimedBy(), t.claimedByName(),
                t.closedBy(), t.closedAt() == null ? 0 : t.closedAt().toEpochMilli(), t.creatorUnread(), t.staffUnread(),
                t.source(), t.sourceId(), t.request() == null ? null : t.request().json());
    }

    /** reports.yml of version 0.1.0: every report becomes a player ticket, the old file is kept as backup. */
    private void migrate(File old) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(old);
        nextId = Math.max(nextId, y.getInt("next-id", 1));
        ConfigurationSection section = y.getConfigurationSection("reports");
        int count = 0;
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection r = section.getConfigurationSection(key);
                try {
                    int id = Integer.parseInt(key);
                    UUID reporter = UUID.fromString(r.getString("reporter"));
                    Instant time = Instant.parse(r.getString("time"));
                    Ticket ticket = new Ticket(id, reporter, r.getString("reporter-name", "?"),
                            UUID.fromString(r.getString("target")), r.getString("target-name", "?"), "other", "", time,
                            r.getString("world", ""), r.getDouble("x"), r.getDouble("y"), r.getDouble("z"));
                    ticket.add(new Ticket.Message(reporter, ticket.creatorName(), false, r.getString("reason", ""), time));
                    ticket.creatorUnread(false);
                    String closedBy = r.getString("closed-by", "");
                    if (!closedBy.isEmpty()) {
                        String closedAt = r.getString("closed-at", "");
                        ticket.close(closedBy, closedAt.isEmpty() ? Instant.now() : Instant.parse(closedAt));
                        ticket.staffUnread(false);
                    }
                    tickets.put(id, ticket);
                    nextId = Math.max(nextId, id + 1);
                    save(ticket);
                    count++;
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("reports.yml: report " + key + " is broken, not migrated (" + e.getMessage() + ").");
                }
            }
        }
        File backup = new File(plugin.getDataFolder(), "reports.migrated.yml");
        if (old.renameTo(backup)) {
            plugin.getLogger().info("Migrated " + count + " reports from reports.yml to tickets (old file: " + backup.getName() + ").");
        } else {
            plugin.getLogger().warning("Migrated " + count + " reports to tickets, but could not rename reports.yml.");
        }
    }

    /** Closed tickets older than keep-closed are removed. */
    private void cleanup() {
        Instant limit = Instant.now().minusMillis(settings().keepClosed());
        List<Integer> old = tickets.values().stream()
                .filter(t -> !t.open() && t.closedAt() != null && t.closedAt().isBefore(limit))
                .map(Ticket::id).toList();
        if (!old.isEmpty()) {
            old.forEach(tickets::remove);
            plugin.storage().deleteTickets(old);
        }
    }

    /**
     * Saves the ticket and the messages added since the last save - in the background (see {@link Storage}); with a
     * database only this ticket and its new messages are written.
     */
    private void save(Ticket ticket) {
        Storage storage = plugin.storage();
        // Open pages in the web interface show the change right away.
        webChanged(ticket);
        ticket.changedHere(System.currentTimeMillis());
        storage.saveTicket(row(ticket), nextId);
        for (Ticket.Message m : ticket.takeUnsaved()) {
            storage.addMessage(ticket.id(), ticket.indexOf(m), new Storage.MessageRow(m.author(), m.authorName(), m.staff(),
                    m.text(), m.time().toEpochMilli()));
        }
    }
}
