package at.marcelr.mradmin.storage;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Where MRadmin keeps its data: YAML files (default), one SQLite file, or MySQL/MariaDB.
 * <p>
 * The plugin keeps working with its data in memory; loading happens once on enable, every change is written in the
 * background on one storage thread (never on the server thread), and {@link #close()} waits until everything is
 * written. Small documents (maintenance state, frozen players, Discord announcement state) are stored as YAML text
 * under a name; tickets get their own rows, so a new message writes only that message.
 * <p>
 * With MySQL/MariaDB several servers (e.g. main server and waiting lobby behind a proxy) can share one database:
 * documents, logs and health stay per server ({@code storage.server-id}), tickets are shared by all of them.
 */
public interface Storage {

    /** "yaml", "sqlite" or "mysql". */
    String type();

    /** A human-readable target for status output, e.g. the file or "host:port/database". */
    String describe();

    // ------------------------------------------------------------- documents

    /** The YAML text saved under {@code name}, or null if there is none. Blocking - only while enabling. */
    String loadState(String name);

    /** Saves the YAML text under {@code name} in the background. */
    void saveState(String name, String yaml);

    // --------------------------------------------------------------- tickets

    /** All tickets with their messages and the next free id. Blocking - only while enabling. */
    Tickets loadTickets();

    /** Inserts or updates the ticket itself (not its messages) in the background. */
    void saveTicket(TicketRow ticket, int nextId);

    /** Appends a message (its position in the conversation is {@code index}) in the background. */
    void addMessage(int ticketId, int index, MessageRow message);

    /** Removes tickets with all their messages in the background. */
    void deleteTickets(Collection<Integer> ids);

    /**
     * Whether several servers may use this storage at the same time (MySQL/MariaDB). Then ticket numbers come from
     * the database ({@link #nextTicketId()}) and changes of the other servers arrive through {@link #pollTickets}.
     */
    default boolean shared() {
        return false;
    }

    /** The next free ticket number of all servers - only with {@link #shared()}. Usually without waiting. */
    default int nextTicketId() {
        throw new UnsupportedOperationException("ticket numbers come from the database only with mysql");
    }

    /**
     * Looks for tickets changed in the last seconds (by any server) - only with {@link #shared()}. Runs on the storage
     * thread; {@code changed} gets the tickets with all their messages there (it may contain unchanged ones too).
     */
    default void pollTickets(java.util.function.Consumer<List<LoadedTicket>> changed) {
    }

    // ------------------------------------------------------ proxy operation (shared only)

    /** What this server reports about itself (YAML text), written in the background - only with {@link #shared()}. */
    default void putServerState(String data) {
    }

    /** What all servers last reported, with its age (by the database clock). Blocking - not on the server thread. */
    default List<ServerState> serverStates() {
        return List.of();
    }

    /** A player may switch to {@code target} into admin mode within {@code ttlMillis}; {@code done} runs afterwards. */
    default void putSwitch(UUID player, String target, long ttlMillis, Runnable done) {
    }

    /** Takes (and deletes) a valid switch of the player to {@code target}. Blocking - not on the server thread. */
    default boolean takeSwitch(UUID player, String target) {
        return false;
    }

    /** Report of a server: {@code data} as written, {@code ageMillis} since it was written. */
    record ServerState(String server, String data, long ageMillis) {
    }

    // ------------------------------------------------------------- audit log

    /** Appends an entry to the log of web actions in the background. */
    void audit(AuditRow row);

    /** The newest {@code limit} entries, newest first. Blocking - call it from a web thread, never the server thread. */
    List<AuditRow> recentAudit(int limit);

    /** Deletes entries older than {@code before} (epoch ms) in the background - retention period, GDPR. */
    void pruneAudit(long before);

    // ---------------------------------------------------------- health history

    /** Appends a point of the health history (as JSON) in the background. */
    void addHealth(long time, String json);

    /** The points since {@code since} (epoch ms), oldest first. Blocking - only while enabling. */
    List<String> loadHealth(long since);

    /** Deletes points older than {@code before} in the background. */
    void pruneHealth(long before);

    /** Size of MRadmin's data in bytes (-1 = unknown). Blocking - not on the server thread. */
    long sizeBytes();

    /** Writes everything still pending and closes connections. */
    void close();

    /** One web action: who (may be null for failed logins), from where, what. Time in epoch milliseconds. */
    record AuditRow(long time, UUID account, String name, String ip, String action, String detail) {
    }

    /** Loaded tickets: rows with their messages (in order), and the next free id. */
    record Tickets(List<LoadedTicket> tickets, int nextId) {
    }

    record LoadedTicket(TicketRow ticket, List<MessageRow> messages) {
    }

    /**
     * One ticket without its messages. Times in epoch milliseconds; {@code closedAt} is 0 while open.
     * Optional values are null. {@code extra}: JSON with the data of tickets from the public web forms (unban
     * requests, contact) - null for normal tickets.
     */
    record TicketRow(int id, UUID creator, String creatorName, UUID target, String targetName, String category,
                     String subject, long created, String world, double x, double y, double z,
                     UUID claimedBy, String claimedByName, String closedBy, long closedAt,
                     boolean creatorUnread, boolean staffUnread, String source, String sourceId,
                     String extra) {
    }

    /** One message of a ticket; time in epoch milliseconds. */
    record MessageRow(UUID author, String authorName, boolean staff, String text, long time) {
    }

    /**
     * Loads the document {@code name} as YAML; empty if it does not exist or is broken (then with a warning).
     * Blocking - only while enabling.
     */
    default org.bukkit.configuration.file.YamlConfiguration loadYaml(String name, java.util.logging.Logger log) {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        String text = loadState(name);
        if (text != null) {
            try {
                yaml.loadFromString(text);
            } catch (org.bukkit.configuration.InvalidConfigurationException e) {
                log.warning("Saved data '" + name + "' is broken, starting without it: " + e.getMessage());
            }
        }
        return yaml;
    }

    /** Saves a YAML document under {@code name} in the background. */
    default void saveYaml(String name, org.bukkit.configuration.file.YamlConfiguration yaml) {
        saveState(name, yaml.saveToString());
    }

    /**
     * The storage configured under {@code storage}. If a database cannot be reached, MRadmin falls back to the YAML
     * files so it keeps working - and says so loudly.
     */
    static Storage create(MRAdmin plugin) {
        Settings.StorageSettings s = plugin.settings().storage();
        if (s.type().equals("yaml")) {
            return new YamlStorage(plugin);
        }
        try {
            return new SqlStorage(plugin, s);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not open the " + s.type() + " storage (" + e.getMessage()
                    + ") - using the YAML files for now. Fix storage in config.yml and restart the server.");
            return new YamlStorage(plugin);
        }
    }
}
