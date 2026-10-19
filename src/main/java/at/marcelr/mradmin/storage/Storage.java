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
     * Optional values are null.
     */
    record TicketRow(int id, UUID creator, String creatorName, UUID target, String targetName, String category,
                     String subject, long created, String world, double x, double y, double z,
                     UUID claimedBy, String claimedByName, String closedBy, long closedAt,
                     boolean creatorUnread, boolean staffUnread, String source, String sourceId) {
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
