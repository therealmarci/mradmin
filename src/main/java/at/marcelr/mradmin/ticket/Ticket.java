package at.marcelr.mradmin.ticket;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One support ticket of a category (a player report, a bug, a question ...) with its conversation between the
 * creator and the team. Changed only on the main thread through {@link TicketManager}, which also saves it.
 */
public final class Ticket {

    /** One message of the conversation; {@code staff} = written by the team (not by the creator). */
    public record Message(UUID author, String authorName, boolean staff, String text, Instant time) {
    }

    private final int id;
    private final UUID creator;
    private final String creatorName;
    /** Reported player (player reports only), otherwise null. */
    private final UUID target;
    private final String targetName;
    /** Category id from tickets.categories (e.g. "cheating", "bug"). */
    private final String category;
    /** Short title of tickets that are not player reports; empty for player reports. */
    private final String subject;
    private final Instant created;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final List<Message> messages = new ArrayList<>();

    private UUID claimedBy;
    private String claimedByName;
    private String closedBy;
    private Instant closedAt;
    /** The team wrote something (or closed it) that the creator has not seen yet. */
    private boolean creatorUnread;
    /** The creator wrote something the team has not seen yet. */
    private boolean staffUnread;
    /** Plugin that opened the ticket automatically (e.g. "MRchunks" for a lag lock), otherwise null. */
    private String source;
    /** What it is about in that plugin (e.g. the chunk loader id). */
    private String sourceId;
    /** Unban or contact request from the public web forms, otherwise null. */
    private WebRequest request;
    /** How many messages are already in the storage (the rest is written with the next save). */
    private int saved;

    Ticket(int id, UUID creator, String creatorName, UUID target, String targetName, String category,
           String subject, Instant created, String world, double x, double y, double z) {
        this.id = id;
        this.creator = creator;
        this.creatorName = creatorName;
        this.target = target;
        this.targetName = targetName;
        this.category = category;
        this.subject = subject == null ? "" : subject;
        this.created = created;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int id() {
        return id;
    }

    /** A report about a player (has a reported player). */
    public boolean playerReport() {
        return target != null;
    }

    public UUID creator() {
        return creator;
    }

    public String creatorName() {
        return creatorName;
    }

    public UUID target() {
        return target;
    }

    public String targetName() {
        return targetName;
    }

    public String category() {
        return category;
    }

    public String subject() {
        return subject;
    }

    public Instant created() {
        return created;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    /** Block coordinates (rounded down - (int) would round -623.5 to -623 instead of the block -624). */
    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public Location location() {
        var w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    /** The first message: what the creator wrote when opening the ticket. */
    public String description() {
        return messages.isEmpty() ? "" : messages.getFirst().text();
    }

    public Message lastMessage() {
        return messages.isEmpty() ? null : messages.getLast();
    }

    /** Time of the last message, closing or creation - used for sorting. */
    public Instant lastActivity() {
        Instant last = messages.isEmpty() ? created : messages.getLast().time();
        return closedAt != null && closedAt.isAfter(last) ? closedAt : last;
    }

    public boolean open() {
        return closedBy == null;
    }

    public boolean isCreator(UUID player) {
        return creator.equals(player);
    }

    public UUID claimedBy() {
        return claimedBy;
    }

    public String claimedByName() {
        return claimedByName;
    }

    public String closedBy() {
        return closedBy;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public boolean creatorUnread() {
        return creatorUnread;
    }

    public boolean staffUnread() {
        return staffUnread;
    }

    public String source() {
        return source;
    }

    public String sourceId() {
        return sourceId;
    }

    /** Unban or contact request from the public web forms, otherwise null. */
    public WebRequest request() {
        return request;
    }

    /** Contact requests from the imprint exist only in the web interface - never in game. */
    public boolean visibleInGame() {
        return request == null || !request.contact();
    }

    /** Opened automatically by a plugin: only the team may close it (the creator must not hide it). */
    public boolean teamCloseOnly() {
        return source != null;
    }

    // ---------------------------------------------------------- changes (manager)

    void add(Message message) {
        messages.add(message);
        if (message.staff()) {
            creatorUnread = true;
        } else {
            staffUnread = true;
        }
    }

    void claim(UUID staff, String name) {
        claimedBy = staff;
        claimedByName = staff == null ? null : name;
    }

    void close(String by, Instant at) {
        closedBy = by;
        closedAt = at;
    }

    void reopen() {
        closedBy = null;
        closedAt = null;
        if (request != null && request.decision() != null) {
            request = request.withDecision(null);
        }
    }

    void creatorUnread(boolean unread) {
        creatorUnread = unread;
    }

    void staffUnread(boolean unread) {
        staffUnread = unread;
    }

    int saved() {
        return saved;
    }

    void saved(int count) {
        saved = count;
    }

    void request(WebRequest request) {
        this.request = request;
    }

    void source(String source, String sourceId) {
        this.source = source;
        this.sourceId = sourceId;
    }
}
