package at.marcelr.mradmin.storage;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite (one file, plugins/MRadmin/mradmin.db) or MySQL/MariaDB. Tables are created on the first start; when they are
 * still empty, the existing YAML files are taken over once (they stay as *.migrated.yml).
 * <p>
 * Every row carries the server id (storage.server-id), so several servers can share one database later. All writes
 * go through one storage thread in the order they were made.
 */
final class SqlStorage implements Storage {

    /** Version of the tables; raise it and add the changes in {@link #migrateSchema} when they change. */
    private static final int SCHEMA = 1;
    /** Documents that were YAML files before. */
    private static final List<String> STATE_FILES = List.of("maintenance", "frozen", "discord-announce");
    private static final String NEXT_ID = "ticket-next-id";

    private final MRAdmin plugin;
    private final String type;
    private final String server;
    private final String p;
    private final HikariDataSource pool;
    private final String target;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MRadmin-Storage");
        thread.setDaemon(true);
        return thread;
    });

    SqlStorage(MRAdmin plugin, Settings.StorageSettings s) throws SQLException {
        this.plugin = plugin;
        this.type = s.type();
        this.server = s.serverId();
        this.p = s.tablePrefix();
        HikariConfig config = new HikariConfig();
        config.setPoolName("MRadmin");
        if (type.equals("sqlite")) {
            File file = new File(plugin.getDataFolder(), "mradmin.db");
            target = "plugins/" + plugin.getDataFolder().getName() + "/" + file.getName();
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            // SQLite allows one writer; a single connection keeps it simple and safe.
            config.setMaximumPoolSize(1);
            config.setConnectionInitSql("PRAGMA busy_timeout = 5000");
        } else {
            target = s.host() + ":" + s.port() + "/" + s.database();
            config.setDriverClassName("org.mariadb.jdbc.Driver");
            config.setJdbcUrl("jdbc:mariadb://" + s.host() + ":" + s.port() + "/" + s.database());
            config.setUsername(s.username());
            config.setPassword(s.password());
            config.setMaximumPoolSize(s.poolSize());
        }
        config.setConnectionTimeout(10_000);
        pool = new HikariDataSource(config);
        try (Connection c = pool.getConnection()) {
            if (type.equals("sqlite")) {
                try (Statement st = c.createStatement()) {
                    st.execute("PRAGMA journal_mode = WAL");
                }
            }
            createTables(c);
            migrateSchema(c);
        } catch (SQLException e) {
            pool.close();
            throw e;
        }
        try {
            importYaml();
        } catch (SQLException e) {
            pool.close();
            throw e;
        }
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String describe() {
        return target;
    }

    // ---------------------------------------------------------------- tables

    private void createTables(Connection c) throws SQLException {
        String suffix = type.equals("mysql") ? " DEFAULT CHARSET=utf8mb4" : "";
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "schema_version (version INT NOT NULL)" + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "tickets ("
                    + "id INT NOT NULL PRIMARY KEY, server VARCHAR(64) NOT NULL, "
                    + "creator VARCHAR(36) NOT NULL, creator_name VARCHAR(64) NOT NULL, "
                    + "target VARCHAR(36), target_name VARCHAR(64), category VARCHAR(64) NOT NULL, "
                    + "subject VARCHAR(255) NOT NULL, created BIGINT NOT NULL, world VARCHAR(128) NOT NULL, "
                    + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
                    + "claimed_by VARCHAR(36), claimed_by_name VARCHAR(64), closed_by VARCHAR(64), closed_at BIGINT, "
                    + "creator_unread INT NOT NULL, staff_unread INT NOT NULL, source VARCHAR(64), source_id VARCHAR(64))"
                    + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "ticket_messages ("
                    + "ticket_id INT NOT NULL, seq INT NOT NULL, author VARCHAR(36) NOT NULL, "
                    + "author_name VARCHAR(64) NOT NULL, staff INT NOT NULL, text TEXT NOT NULL, time BIGINT NOT NULL, "
                    + "PRIMARY KEY (ticket_id, seq))" + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "state ("
                    + "server VARCHAR(64) NOT NULL, name VARCHAR(64) NOT NULL, content TEXT NOT NULL, "
                    + "PRIMARY KEY (server, name))" + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "audit_log ("
                    + "server VARCHAR(64) NOT NULL, time BIGINT NOT NULL, account VARCHAR(36), name VARCHAR(64), "
                    + "ip VARCHAR(64), action VARCHAR(64) NOT NULL, detail TEXT)" + suffix);
        }
    }

    /** Brings older tables up to {@link #SCHEMA}; a fresh database just gets the version number. */
    private void migrateSchema(Connection c) throws SQLException {
        int version = 0;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT version FROM " + p + "schema_version")) {
            if (rs.next()) {
                version = rs.getInt(1);
            }
        }
        if (version > SCHEMA) {
            plugin.getLogger().warning("The database was used by a newer MRadmin (table version " + version
                    + "). Older versions may not understand everything in it.");
            return;
        }
        if (version == SCHEMA) {
            return;
        }
        // Future changes: if (version < 2) { ALTER TABLE ... }
        try (Statement st = c.createStatement()) {
            st.execute("DELETE FROM " + p + "schema_version");
            st.execute("INSERT INTO " + p + "schema_version (version) VALUES (" + SCHEMA + ")");
        }
    }

    /** First start with a database: take over tickets.yml and the YAML documents, keep them as *.migrated.yml. */
    private void importYaml() throws SQLException {
        File folder = plugin.getDataFolder();
        File ticketFile = new File(folder, "tickets.yml");
        try (Connection c = pool.getConnection()) {
            if (ticketFile.exists() && count(c, "tickets") == 0) {
                Tickets old = YamlStorage.read(ticketFile, plugin);
                c.setAutoCommit(false);
                for (LoadedTicket t : old.tickets()) {
                    writeTicket(c, t.ticket());
                    for (int i = 0; i < t.messages().size(); i++) {
                        writeMessage(c, t.ticket().id(), i, t.messages().get(i));
                    }
                }
                writeState(c, NEXT_ID, String.valueOf(old.nextId()));
                c.commit();
                c.setAutoCommit(true);
                retire(ticketFile);
                plugin.getLogger().info("Moved " + old.tickets().size() + " tickets from tickets.yml into the " + type + " storage.");
            }
            for (String name : STATE_FILES) {
                File file = new File(folder, name + ".yml");
                if (file.exists() && readState(c, name) == null) {
                    writeState(c, name, java.nio.file.Files.readString(file.toPath()));
                    retire(file);
                    plugin.getLogger().info("Moved " + file.getName() + " into the " + type + " storage.");
                }
            }
        } catch (java.io.IOException e) {
            throw new SQLException("could not read the YAML files: " + e.getMessage(), e);
        }
    }

    private void retire(File file) {
        File backup = new File(file.getParentFile(), file.getName().replace(".yml", ".migrated.yml"));
        if (!file.renameTo(backup)) {
            plugin.getLogger().warning("Could not rename " + file.getName() + " - it is no longer used, delete it by hand.");
        }
    }

    private int count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + p + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ------------------------------------------------------------- documents

    @Override
    public String loadState(String name) {
        try (Connection c = pool.getConnection()) {
            return readState(c, name);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read '" + name + "' from the database: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void saveState(String name, String yaml) {
        write("save '" + name + "'", c -> writeState(c, name, yaml));
    }

    private String readState(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT content FROM " + p + "state WHERE server = ? AND name = ?")) {
            ps.setString(1, server);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void writeState(Connection c, String name, String content) throws SQLException {
        // REPLACE works in SQLite and MySQL/MariaDB alike.
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p + "state (server, name, content) VALUES (?, ?, ?)")) {
            ps.setString(1, server);
            ps.setString(2, name);
            ps.setString(3, content);
            ps.executeUpdate();
        }
    }

    // --------------------------------------------------------------- tickets

    @Override
    public Tickets loadTickets() {
        try (Connection c = pool.getConnection()) {
            Map<Integer, List<MessageRow>> messages = new HashMap<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT ticket_id, author, author_name, staff, text, time FROM " + p + "ticket_messages ORDER BY ticket_id, seq")) {
                while (rs.next()) {
                    messages.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(new MessageRow(
                            UUID.fromString(rs.getString(2)), rs.getString(3), rs.getInt(4) != 0, rs.getString(5), rs.getLong(6)));
                }
            }
            Map<Integer, LoadedTicket> tickets = new LinkedHashMap<>();
            int next = 1;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM " + p + "tickets ORDER BY id")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    long closedAt = rs.getLong("closed_at");
                    TicketRow row = new TicketRow(id, UUID.fromString(rs.getString("creator")), rs.getString("creator_name"),
                            uuid(rs.getString("target")), rs.getString("target_name"), rs.getString("category"),
                            rs.getString("subject"), rs.getLong("created"), rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            uuid(rs.getString("claimed_by")), rs.getString("claimed_by_name"), rs.getString("closed_by"),
                            rs.wasNull() ? 0 : closedAt, rs.getInt("creator_unread") != 0, rs.getInt("staff_unread") != 0,
                            rs.getString("source"), rs.getString("source_id"));
                    tickets.put(id, new LoadedTicket(row, List.copyOf(messages.getOrDefault(id, List.of()))));
                    next = Math.max(next, id + 1);
                }
            }
            String stored = readState(c, NEXT_ID);
            if (stored != null) {
                try {
                    next = Math.max(next, Integer.parseInt(stored.trim()));
                } catch (NumberFormatException ignored) {
                    // broken counter: the highest id decides
                }
            }
            return new Tickets(List.copyOf(tickets.values()), next);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load the tickets from the database: " + e.getMessage());
            return new Tickets(List.of(), 1);
        }
    }

    private static UUID uuid(String raw) {
        return raw == null || raw.isEmpty() ? null : UUID.fromString(raw);
    }

    @Override
    public void saveTicket(TicketRow ticket, int nextId) {
        write("save ticket #" + ticket.id(), c -> {
            writeTicket(c, ticket);
            writeState(c, NEXT_ID, String.valueOf(nextId));
        });
    }

    private void writeTicket(Connection c, TicketRow t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p + "tickets (id, server, creator, creator_name, "
                + "target, target_name, category, subject, created, world, x, y, z, claimed_by, claimed_by_name, closed_by, "
                + "closed_at, creator_unread, staff_unread, source, source_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, t.id());
            ps.setString(2, server);
            ps.setString(3, t.creator().toString());
            ps.setString(4, t.creatorName());
            ps.setString(5, t.target() == null ? null : t.target().toString());
            ps.setString(6, t.targetName());
            ps.setString(7, t.category());
            ps.setString(8, t.subject());
            ps.setLong(9, t.created());
            ps.setString(10, t.world());
            ps.setDouble(11, t.x());
            ps.setDouble(12, t.y());
            ps.setDouble(13, t.z());
            ps.setString(14, t.claimedBy() == null ? null : t.claimedBy().toString());
            ps.setString(15, t.claimedByName());
            ps.setString(16, t.closedBy());
            if (t.closedBy() == null) {
                ps.setNull(17, Types.BIGINT);
            } else {
                ps.setLong(17, t.closedAt());
            }
            ps.setInt(18, t.creatorUnread() ? 1 : 0);
            ps.setInt(19, t.staffUnread() ? 1 : 0);
            ps.setString(20, t.source());
            ps.setString(21, t.sourceId());
            ps.executeUpdate();
        }
    }

    @Override
    public void addMessage(int ticketId, int index, MessageRow message) {
        write("save a message of ticket #" + ticketId, c -> writeMessage(c, ticketId, index, message));
    }

    private void writeMessage(Connection c, int ticketId, int index, MessageRow m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p + "ticket_messages "
                + "(ticket_id, seq, author, author_name, staff, text, time) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, ticketId);
            ps.setInt(2, index);
            ps.setString(3, m.author().toString());
            ps.setString(4, m.authorName());
            ps.setInt(5, m.staff() ? 1 : 0);
            ps.setString(6, m.text());
            ps.setLong(7, m.time());
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteTickets(Collection<Integer> ids) {
        List<Integer> copy = List.copyOf(ids);
        write("delete " + copy.size() + " tickets", c -> {
            try (PreparedStatement tickets = c.prepareStatement("DELETE FROM " + p + "tickets WHERE id = ?");
                 PreparedStatement messages = c.prepareStatement("DELETE FROM " + p + "ticket_messages WHERE ticket_id = ?")) {
                for (int id : copy) {
                    tickets.setInt(1, id);
                    tickets.executeUpdate();
                    messages.setInt(1, id);
                    messages.executeUpdate();
                }
            }
        });
    }

    // ------------------------------------------------------------- audit log

    @Override
    public void audit(AuditRow row) {
        write("write the web log", c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + p + "audit_log "
                    + "(server, time, account, name, ip, action, detail) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, server);
                ps.setLong(2, row.time());
                ps.setString(3, row.account() == null ? null : row.account().toString());
                ps.setString(4, row.name());
                ps.setString(5, row.ip());
                ps.setString(6, row.action());
                ps.setString(7, row.detail());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<AuditRow> recentAudit(int limit) {
        List<AuditRow> result = new ArrayList<>();
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT time, account, name, ip, action, detail FROM " + p + "audit_log WHERE server = ? "
                        + "ORDER BY time DESC LIMIT ?")) {
            ps.setString(1, server);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AuditRow(rs.getLong(1), uuid(rs.getString(2)), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not read the web log: " + e.getMessage());
        }
        return result;
    }

    // ---------------------------------------------------------------- writing

    private interface Write {
        void run(Connection c) throws SQLException;
    }

    /** Runs a write on the storage thread; a failure is logged (the data stays in memory and is written next time). */
    private void write(String what, Write write) {
        executor.execute(() -> {
            try (Connection c = pool.getConnection()) {
                write.run(c);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not " + what + " in the database: " + e.getMessage());
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Saving to the database took too long - some changes may be lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.close();
    }
}
