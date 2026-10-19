package at.marcelr.mradmin.ticket;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
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
    public static final String PERM_CREATE = "mradmin.report";
    public static final String PERM_NOTIFY = "mradmin.report.notify";
    public static final String PERM_MANAGE = "mradmin.report.manage";

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
        if (message.staff() && !settings().showStaffNames() && !viewer.hasPermission(PERM_MANAGE)) {
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
        if (player.hasPermission(PERM_MANAGE)) {
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
        Ticket ticket = new Ticket(nextId++, creator.getUniqueId(), creator.getName(),
                target == null ? null : target.getUniqueId(), targetName, category.id(),
                cleanSubject, Instant.now(), where.getWorld().getName(), where.getX(), where.getY(), where.getZ());
        ticket.add(new Ticket.Message(creator.getUniqueId(), creator.getName(), false, cleanText, Instant.now()));
        tickets.put(ticket.id(), ticket);
        save(ticket);

        String categoryName = categoryName(category.id());
        messages().send(creator, "ticket-created", tag("id", ticket.id()), tag("type", categoryName),
                tag("open", openButton(ticket)));
        notifyNew(ticket);
        plugin.getLogger().info("Ticket #" + ticket.id() + " (" + category.id() + ") by " + creator.getName() + ": "
                + summary(ticket) + " - " + TicketText.preview(cleanText, 200));
        plugin.discord().log("ticket", "id", ticket.id(), "type", categoryName, "creator", creator.getName(),
                "summary", summary(ticket), "text", TicketText.preview(cleanText, 300),
                "world", ticket.world(), "x", (int) ticket.x(), "y", (int) ticket.y(), "z", (int) ticket.z());
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
     * A chunk loader was locked or unlocked in MRchunks (values of its LoaderLockEvent). Lock: a ticket for the owner
     * with all details, in which they can explain what happened and which only the team can close - or, if one is
     * still open, a note in it. Unlock: a note in the open ticket. The owner is told in chat with a link.
     */
    public void loaderLock(String source, Map<?, ?> data) {
        String type = string(data, "type");
        String id = string(data, "loader");
        String name = string(data, "name");
        Ticket open = bySource(source, id);
        if (type.equals("unlock")) {
            if (open != null) {
                note(open, source, messages().plain("ticket-lock-unlocked", "admin", string(data, "by")), false);
            }
            return;
        }
        if (!type.equals("lock") || !(data.get("owner") instanceof UUID owner)) {
            return;
        }
        String details = string(data, "details");
        if (open != null) {
            note(open, source, messages().plain("ticket-lock-again") + "\n" + details, true);
            notifyLock(open, owner, name);
            notifyStaff(open, null, "ticket-notify-message", tag("id", open.id()), tag("player", source),
                    tag("text", TicketText.preview(details, PREVIEW)), tag("open", openButton(open)));
            return;
        }
        String subject = TicketText.limit(messages().plain("ticket-lock-subject", "name", name), 100);
        Ticket ticket = new Ticket(nextId++, owner, string(data, "owner-name"), null, null, LOCK_CATEGORY, subject,
                Instant.now(), string(data, "world"), number(data, "x") + 0.5, number(data, "y") + 1, number(data, "z") + 0.5);
        ticket.source(source, id);
        ticket.add(new Ticket.Message(SYSTEM, source, true, details, Instant.now()));
        ticket.staffUnread(true);
        tickets.put(ticket.id(), ticket);
        save(ticket);

        notifyLock(ticket, owner, name);
        notifyNew(ticket);
        plugin.getLogger().info("Ticket #" + ticket.id() + " (" + LOCK_CATEGORY + ") for " + ticket.creatorName()
                + ": " + subject + " (opened by " + source + ").");
        plugin.discord().log("ticket", "id", ticket.id(), "type", categoryName(LOCK_CATEGORY), "creator", ticket.creatorName(),
                "summary", subject, "text", TicketText.preview(details, 300),
                "world", ticket.world(), "x", (int) ticket.x(), "y", (int) ticket.y(), "z", (int) ticket.z());
    }

    /** A message of the plugin itself in the ticket (the creator sees it as unread; the team too if asked). */
    private void note(Ticket ticket, String source, String text, boolean forStaff) {
        ticket.add(new Ticket.Message(SYSTEM, source, true, text, Instant.now()));
        if (forStaff) {
            ticket.staffUnread(true);
        }
        save(ticket);
    }

    /** Tells the owner (if online) that the ticket is there and that they can explain themselves in it. */
    private void notifyLock(Ticket ticket, UUID owner, String name) {
        Player player = Bukkit.getPlayer(owner);
        if (player != null) {
            messages().send(player, "ticket-lock-notify", tag("id", ticket.id()), tag("name", name),
                    tag("open", openButton(ticket)));
            sound(player);
        }
    }

    private static String string(Map<?, ?> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(Map<?, ?> data, String key) {
        return data.get(key) instanceof Number n ? n.doubleValue() : 0;
    }

    // ---------------------------------------------------------------- handling

    /** The creator and the team may open a ticket. */
    public boolean canView(Player player, Ticket ticket) {
        return ticket.isCreator(player.getUniqueId()) || player.hasPermission(PERM_MANAGE);
    }

    /** Whether {@code player} writes as team member in this ticket (team, but not the creator). */
    public boolean asStaff(Player player, Ticket ticket) {
        return !ticket.isCreator(player.getUniqueId()) && player.hasPermission(PERM_MANAGE);
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
        if (last != null && !author.hasPermission(PERM_MANAGE) && Instant.now().isBefore(last.plusMillis(s.replyCooldown()))) {
            messages().send(author, "ticket-reply-cooldown", tag("time",
                    Texts.duration(last.plusMillis(s.replyCooldown()).toEpochMilli() - System.currentTimeMillis())));
            return false;
        }
        lastReply.put(author.getUniqueId(), Instant.now());
        boolean staff = asStaff(author, ticket);
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
                messages().send(creator, "ticket-answer", tag("id", ticket.id()), tag("open", openButton(ticket)),
                        tag("text", TicketText.preview(clean, PREVIEW)));
                sound(creator);
            }
        } else {
            notifyStaff(ticket, author, "ticket-notify-message", tag("id", ticket.id()), tag("player", author.getName()),
                    tag("text", TicketText.preview(clean, PREVIEW)), tag("open", openButton(ticket)));
        }
        plugin.discord().log("ticket-message", "id", ticket.id(), "author", author.getName(),
                "text", TicketText.preview(clean, 300), "summary", summary(ticket));
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
        if (ticket.teamCloseOnly() && !by.hasPermission(PERM_MANAGE)) {
            messages().send(by, "ticket-close-team-only");
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
                        tag("summary", summary(ticket)), tag("open", openButton(ticket)));
                sound(creator);
            }
        } else if (ticket.claimedBy() != null) {
            Player claimer = Bukkit.getPlayer(ticket.claimedBy());
            if (claimer != null && !claimer.equals(by)) {
                messages().send(claimer, "ticket-closed-by-creator", tag("id", ticket.id()), tag("player", by.getName()));
            }
        }
        plugin.discord().log("ticket-closed", "id", ticket.id(), "admin", by.getName(), "summary", summary(ticket));
    }

    /** Opens a closed ticket again (team only). */
    public void reopen(Player staff, Ticket ticket) {
        if (!staff.hasPermission(PERM_MANAGE)) {
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
            messages().send(creator, "ticket-reopened-creator", tag("id", ticket.id()), tag("open", openButton(ticket)));
        }
        plugin.discord().log("ticket-reopened", "id", ticket.id(), "admin", staff.getName(), "summary", summary(ticket));
    }

    /** Claims the ticket for {@code staff}, or releases it if they already have it. */
    public void toggleClaim(Player staff, Ticket ticket) {
        if (!staff.hasPermission(PERM_MANAGE)) {
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

    /** Teleports staff to the reported player (if online) or to where the ticket was opened. */
    public void teleport(Player staff, Ticket ticket) {
        if (!staff.hasPermission(PERM_MANAGE)) {
            messages().send(staff, "no-permission");
            return;
        }
        if (!plugin.feature(staff, at.marcelr.mradmin.Feature.TELEPORT)) {
            return;
        }
        Player target = ticket.target() == null ? null : Bukkit.getPlayer(ticket.target());
        Location to = target != null ? target.getLocation() : ticket.location();
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
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(PERM_NOTIFY) && !ticket.isCreator(staff.getUniqueId())) {
                messages().send(staff, "ticket-notify-new", tag("id", ticket.id()), tag("type", categoryName(ticket.category())),
                        tag("player", ticket.creatorName()), tag("summary", summary(ticket)),
                        tag("text", TicketText.preview(ticket.description(), PREVIEW)),
                        tag("open", openButton(ticket)), tag("teleport", teleportButton(ticket)), tag("list", listButton()));
                sound(staff);
            }
        }
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
        int unread = (int) tickets.values().stream().filter(t -> t.isCreator(player.getUniqueId()) && t.creatorUnread()).count();
        if (unread > 0) {
            messages().send(player, "ticket-join-unread", tag("count", unread), tag("list", messages().get("ticket-button-mine")
                    .clickEvent(ClickEvent.runCommand("/report"))
                    .hoverEvent(HoverEvent.showText(messages().get("ticket-button-mine-hover")))));
        }
        int open = openCount();
        if (open > 0 && player.hasPermission(PERM_NOTIFY)) {
            messages().send(player, "ticket-join-staff", tag("count", open), tag("unread", staffUnreadCount()),
                    tag("list", listButton()));
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
        ticket.saved(messages.size());
        return ticket;
    }

    private static Storage.TicketRow row(Ticket t) {
        return new Storage.TicketRow(t.id(), t.creator(), t.creatorName(), t.target(), t.targetName(), t.category(),
                t.subject(), t.created().toEpochMilli(), t.world(), t.x(), t.y(), t.z(), t.claimedBy(), t.claimedByName(),
                t.closedBy(), t.closedAt() == null ? 0 : t.closedAt().toEpochMilli(), t.creatorUnread(), t.staffUnread(),
                t.source(), t.sourceId());
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
        storage.saveTicket(row(ticket), nextId);
        List<Ticket.Message> all = ticket.messages();
        for (int i = ticket.saved(); i < all.size(); i++) {
            Ticket.Message m = all.get(i);
            storage.addMessage(ticket.id(), i, new Storage.MessageRow(m.author(), m.authorName(), m.staff(), m.text(),
                    m.time().toEpochMilli()));
        }
        ticket.saved(all.size());
    }
}
