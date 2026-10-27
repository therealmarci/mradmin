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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * SQLite (one file, plugins/MRadmin/mradmin.db) or MySQL/MariaDB. Tables are created on the first start; when they are
 * still empty, the existing YAML files are taken over once (they stay as *.migrated.yml).
 * <p>
 * Every row carries the server id (storage.server-id), so several servers can share one database. All writes go
 * through one storage thread in the order they were made.
 * <p>
 * With MySQL/MariaDB the tickets are shared by all servers: the number comes from a counter in the database (one is
 * always reserved ahead, so opening a ticket does not wait), a changed ticket writes only the columns that changed
 * (two servers changing different things of one ticket do not overwrite each other), messages get the next free
 * position in the database, and {@link #pollTickets} finds what the other servers changed (columns {@code updated} and
 * {@code stored} with the time of the database, so the clocks of the servers do not matter). On the first start with
 * MySQL an existing {@code mradmin.db} (SQLite) is taken over once, like the YAML files.
 */
final class SqlStorage implements Storage {

    /** Version of the tables; raise it and add the changes in {@link #migrateSchema} when they change. */
    private static final int SCHEMA = 3;
    /** Documents that were YAML files before. */
    private static final List<String> STATE_FILES = List.of("maintenance", "frozen", "discord-announce", "web-accounts",
            "web-sessions", "web-requests", "web-push");
    private static final String NEXT_ID = "ticket-next-id";
    /** Name of the ticket number counter in the table counters (MySQL). */
    private static final String TICKET_COUNTER = "ticket-id";
    /** Changes are searched this far back on every poll, so writes that took a moment are never missed. */
    private static final long POLL_OVERLAP_MILLIS = 15_000;
    private static final String[] TABLES = {"schema_version", "tickets", "ticket_messages", "state", "audit_log",
            "health", "counters", "server_state", "proxy_switch"};

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
    /** MySQL: the ticket rows as they are in the database - a save writes only what differs. */
    private final Map<Integer, TicketRow> known = new ConcurrentHashMap<>();
    /** MySQL: a ticket number reserved ahead (0 = none). */
    private final AtomicInteger reserved = new AtomicInteger();
    /** MySQL: database time of the last poll (epoch ms). Storage thread. */
    private long lastPoll;

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
            if (shared()) {
                importSqlite();
            }
            importYaml();
            if (shared()) {
                initCounter();
                reserved.set(reserveId());
            }
        } catch (SQLException e) {
            pool.close();
            throw e;
        }
    }

    @Override
    public boolean shared() {
        return type.equals("mysql");
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String describe() {
        return target;
    }

    @Override
    public long sizeBytes() {
        if (type.equals("sqlite")) {
            long size = 0;
            for (String suffix : new String[] {"", "-wal", "-shm"}) {
                size += new File(plugin.getDataFolder(), "mradmin.db" + suffix).length();
            }
            return size;
        }
        // MariaDB / MySQL: data and indexes of MRadmin's tables (shared by all servers using them).
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables"
                        + " WHERE table_schema = DATABASE() AND table_name IN ("
                        + String.join(", ", Collections.nCopies(TABLES.length, "?")) + ")")) {
            for (int i = 0; i < TABLES.length; i++) {
                ps.setString(i + 1, p + TABLES[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            return -1;
        }
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
                    + "creator_unread INT NOT NULL, staff_unread INT NOT NULL, source VARCHAR(64), source_id VARCHAR(64), extra TEXT, "
                    + "updated BIGINT NOT NULL DEFAULT 0)" + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "ticket_messages ("
                    + "ticket_id INT NOT NULL, seq INT NOT NULL, author VARCHAR(36) NOT NULL, "
                    + "author_name VARCHAR(64) NOT NULL, staff INT NOT NULL, text TEXT NOT NULL, time BIGINT NOT NULL, "
                    + "stored BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (ticket_id, seq))" + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "state ("
                    + "server VARCHAR(64) NOT NULL, name VARCHAR(64) NOT NULL, content TEXT NOT NULL, "
                    + "PRIMARY KEY (server, name))" + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "audit_log ("
                    + "server VARCHAR(64) NOT NULL, time BIGINT NOT NULL, account VARCHAR(36), name VARCHAR(64), "
                    + "ip VARCHAR(64), action VARCHAR(64) NOT NULL, detail TEXT)" + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "health ("
                    + "server VARCHAR(64) NOT NULL, time BIGINT NOT NULL, data TEXT NOT NULL)" + suffix);
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "counters ("
                    + "name VARCHAR(64) NOT NULL PRIMARY KEY, value BIGINT NOT NULL)" + suffix);
            // Proxy operation: what every server reports about itself (main server ready / stopping, maintenance) ...
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "server_state ("
                    + "server VARCHAR(64) NOT NULL PRIMARY KEY, data TEXT NOT NULL, updated BIGINT NOT NULL)" + suffix);
            // ... and admins who switch from the waiting lobby to the main server into admin mode.
            st.execute("CREATE TABLE IF NOT EXISTS " + p + "proxy_switch ("
                    + "player VARCHAR(36) NOT NULL PRIMARY KEY, target VARCHAR(64) NOT NULL, expires BIGINT NOT NULL)" + suffix);
        }
    }

    /** The current time of the database in epoch milliseconds, as SQL. */
    private String now() {
        return type.equals("mysql") ? "ROUND(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)"
                : "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)";
    }

    private long dbNow(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT " + now())) {
            return rs.next() ? rs.getLong(1) : System.currentTimeMillis();
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
        // A fresh database (version 0) already has every column from createTables.
        if (version == 1) {
            // 2: data of tickets from the public web forms (unban requests, contact)
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE " + p + "tickets ADD COLUMN extra TEXT");
            }
        }
        if (version >= 1 && version < 3) {
            // 3: shared tickets - when a ticket and a message were last written (time of the database)
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE " + p + "tickets ADD COLUMN updated BIGINT NOT NULL DEFAULT 0");
                st.execute("ALTER TABLE " + p + "ticket_messages ADD COLUMN stored BIGINT NOT NULL DEFAULT 0");
            }
        }
        if (version < 3) {
            // Indexes for finding recent changes. Two servers starting on a fresh database at the same moment may both
            // try - the second one just fails, which is fine.
            createIndex(c, "tickets", "updated");
            createIndex(c, "ticket_messages", "stored");
        }
        try (Statement st = c.createStatement()) {
            st.execute("DELETE FROM " + p + "schema_version");
            st.execute("INSERT INTO " + p + "schema_version (version) VALUES (" + SCHEMA + ")");
        }
    }

    private void createIndex(Connection c, String table, String column) {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE INDEX " + p + table + "_" + column + " ON " + p + table + " (" + column + ")");
        } catch (SQLException e) {
            plugin.getLogger().fine("Index on " + table + "." + column + " not created: " + e.getMessage());
        }
    }

    /**
     * First start with MySQL: take over an existing mradmin.db (SQLite) - all tickets, documents, logs and health
     * points, with the server ids they have - if the tickets and documents of the database are still empty. The file
     * stays as mradmin.migrated.db.
     */
    private void importSqlite() throws SQLException {
        File file = new File(plugin.getDataFolder(), "mradmin.db");
        if (!file.exists()) {
            return;
        }
        try (Connection c = pool.getConnection()) {
            if (count(c, "tickets") > 0 || count(c, "state") > 0) {
                return;
            }
            int tickets;
            try (HikariDataSource sqlite = sqlitePool(file); Connection old = sqlite.getConnection()) {
                c.setAutoCommit(false);
                tickets = copyTable(old, c, "tickets");
                copyTable(old, c, "ticket_messages");
                copyTable(old, c, "state");
                copyTable(old, c, "audit_log");
                copyTable(old, c, "health");
                c.commit();
                c.setAutoCommit(true);
            } catch (SQLException e) {
                c.rollback();
                c.setAutoCommit(true);
                throw new SQLException("could not take over mradmin.db: " + e.getMessage(), e);
            }
            for (String suffix : new String[] {"", "-wal", "-shm"}) {
                File part = new File(plugin.getDataFolder(), "mradmin.db" + suffix);
                if (part.exists() && !part.renameTo(new File(plugin.getDataFolder(), "mradmin.migrated.db" + suffix))) {
                    plugin.getLogger().warning("Could not rename " + part.getName() + " - it is no longer used, delete it by hand.");
                }
            }
            plugin.getLogger().info("Moved " + tickets + " tickets and all other data from mradmin.db into the mysql storage.");
        }
    }

    /** Copies all rows of {@code table} (the columns both sides have) from {@code from} to {@code to}. */
    private int copyTable(Connection from, Connection to, String table) throws SQLException {
        List<String> target = new ArrayList<>();
        try (ResultSet rs = to.getMetaData().getColumns(null, null, p + table, null)) {
            while (rs.next()) {
                target.add(rs.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
            }
        }
        int rows = 0;
        try (Statement st = from.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM " + p + table)) {
            var meta = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String name = meta.getColumnName(i).toLowerCase(java.util.Locale.ROOT);
                if (target.contains(name)) {
                    columns.add(name);
                }
            }
            try (PreparedStatement insert = to.prepareStatement("INSERT INTO " + p + table + " (" + String.join(", ", columns)
                    + ") VALUES (" + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")")) {
                while (rs.next()) {
                    for (int i = 0; i < columns.size(); i++) {
                        insert.setObject(i + 1, rs.getObject(columns.get(i)));
                    }
                    insert.addBatch();
                    rows++;
                }
                insert.executeBatch();
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("no such table")) {
                return 0;
            }
            throw e;
        }
        return rows;
    }

    /** MySQL: the ticket counter starts after the highest number any server used so far. */
    private void initCounter() throws SQLException {
        try (Connection c = pool.getConnection()) {
            long next = 1;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(id) FROM " + p + "tickets")) {
                if (rs.next()) {
                    next = rs.getLong(1) + 1;
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT content FROM " + p + "state WHERE name = ?")) {
                ps.setString(1, NEXT_ID);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            next = Math.max(next, Long.parseLong(rs.getString(1).trim()));
                        } catch (NumberFormatException ignored) {
                            // broken counter of an older version: the highest id decides
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + p + "counters (name, value) VALUES (?, ?)")) {
                ps.setString(1, TICKET_COUNTER);
                ps.setLong(2, next);
                ps.executeUpdate();
            }
            // Never below the highest ticket (e.g. after tickets were added by hand or an import).
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + p + "counters SET value = ? WHERE name = ? AND value < ?")) {
                ps.setLong(1, next);
                ps.setString(2, TICKET_COUNTER);
                ps.setLong(3, next);
                ps.executeUpdate();
            }
        }
    }

    /** MySQL: takes the next ticket number from the counter (atomic for all servers). */
    private int reserveId() throws SQLException {
        try (Connection c = pool.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + p + "counters SET value = LAST_INSERT_ID(value + 1) WHERE name = ?")) {
                ps.setString(1, TICKET_COUNTER);
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("ticket counter missing");
                }
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
                rs.next();
                return (int) (rs.getLong(1) - 1);
            }
        }
    }

    @Override
    public int nextTicketId() {
        int id = reserved.getAndSet(0);
        if (id <= 0) {
            // Nothing reserved (several tickets in a row, or the database was gone): ask right away.
            try {
                id = reserveId();
            } catch (SQLException e) {
                throw new IllegalStateException("could not get a ticket number from the database: " + e.getMessage(), e);
            }
        }
        executor.execute(() -> {
            try {
                if (reserved.get() == 0) {
                    reserved.compareAndSet(0, reserveId());
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not reserve the next ticket number: " + e.getMessage());
            }
        });
        return id;
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

    /** A short-lived connection to an old mradmin.db (read only). */
    private static HikariDataSource sqlitePool(File file) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("MRadmin-Import");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
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
            if (shared()) {
                lastPoll = dbNow(c);
            }
            Map<Integer, List<MessageRow>> messages = new HashMap<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT ticket_id, author, author_name, staff, text, time FROM " + p + "ticket_messages ORDER BY ticket_id, seq")) {
                while (rs.next()) {
                    messages.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(message(rs));
                }
            }
            Map<Integer, LoadedTicket> tickets = new LinkedHashMap<>();
            int next = 1;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM " + p + "tickets ORDER BY id")) {
                while (rs.next()) {
                    TicketRow row = ticket(rs);
                    tickets.put(row.id(), new LoadedTicket(row, List.copyOf(messages.getOrDefault(row.id(), List.of()))));
                    next = Math.max(next, row.id() + 1);
                    if (shared()) {
                        known.put(row.id(), row);
                    }
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

    /** The message in the current row of {@code ticket_id, author, author_name, staff, text, time}. */
    private static MessageRow message(ResultSet rs) throws SQLException {
        return new MessageRow(UUID.fromString(rs.getString(2)), rs.getString(3), rs.getInt(4) != 0, rs.getString(5), rs.getLong(6));
    }

    /** The ticket in the current row of {@code SELECT * FROM tickets}. */
    private static TicketRow ticket(ResultSet rs) throws SQLException {
        long closedAt = rs.getLong("closed_at");
        boolean noClose = rs.wasNull();
        return new TicketRow(rs.getInt("id"), UUID.fromString(rs.getString("creator")), rs.getString("creator_name"),
                uuid(rs.getString("target")), rs.getString("target_name"), rs.getString("category"),
                rs.getString("subject"), rs.getLong("created"), rs.getString("world"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                uuid(rs.getString("claimed_by")), rs.getString("claimed_by_name"), rs.getString("closed_by"),
                noClose ? 0 : closedAt, rs.getInt("creator_unread") != 0, rs.getInt("staff_unread") != 0,
                rs.getString("source"), rs.getString("source_id"), rs.getString("extra"));
    }

    private static UUID uuid(String raw) {
        return raw == null || raw.isEmpty() ? null : UUID.fromString(raw);
    }

    @Override
    public void saveTicket(TicketRow ticket, int nextId) {
        write("save ticket #" + ticket.id(), c -> {
            if (shared()) {
                writeShared(c, ticket);
            } else {
                writeTicket(c, ticket);
                writeState(c, NEXT_ID, String.valueOf(nextId));
            }
        });
    }

    private void writeTicket(Connection c, TicketRow t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p + "tickets (id, server, creator, creator_name, "
                + "target, target_name, category, subject, created, world, x, y, z, claimed_by, claimed_by_name, closed_by, "
                + "closed_at, creator_unread, staff_unread, source, source_id, extra, updated) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + now() + ")")) {
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
            ps.setString(22, t.extra());
            ps.executeUpdate();
        }
    }

    /**
     * MySQL: a new ticket is inserted, a known one gets only the columns that differ from the database - so changes
     * of another server to other columns (e.g. claimed there, read here) are kept.
     */
    private void writeShared(Connection c, TicketRow t) throws SQLException {
        TicketRow before = known.get(t.id());
        if (before == null) {
            writeTicket(c, t);
            known.put(t.id(), t);
            return;
        }
        Map<String, Object> changed = new LinkedHashMap<>();
        diff(changed, "claimed_by", str(before.claimedBy()), str(t.claimedBy()));
        diff(changed, "claimed_by_name", before.claimedByName(), t.claimedByName());
        diff(changed, "closed_by", before.closedBy(), t.closedBy());
        diff(changed, "closed_at", before.closedBy() == null ? null : before.closedAt(), t.closedBy() == null ? null : t.closedAt());
        diff(changed, "creator_unread", before.creatorUnread() ? 1 : 0, t.creatorUnread() ? 1 : 0);
        diff(changed, "staff_unread", before.staffUnread() ? 1 : 0, t.staffUnread() ? 1 : 0);
        diff(changed, "source", before.source(), t.source());
        diff(changed, "source_id", before.sourceId(), t.sourceId());
        diff(changed, "extra", before.extra(), t.extra());
        if (changed.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("UPDATE " + p + "tickets SET ");
        for (String column : changed.keySet()) {
            sql.append(column).append(" = ?, ");
        }
        sql.append("updated = ").append(now()).append(" WHERE id = ?");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            for (Object value : changed.values()) {
                ps.setObject(i++, value);
            }
            ps.setInt(i, t.id());
            if (ps.executeUpdate() == 0) {
                // Deleted meanwhile (e.g. cleaned up by another server) - write it again as a whole.
                writeTicket(c, t);
            }
        }
        known.put(t.id(), t);
    }

    private static void diff(Map<String, Object> changed, String column, Object before, Object now) {
        if (!java.util.Objects.equals(before, now)) {
            changed.put(column, now);
        }
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }

    @Override
    public void addMessage(int ticketId, int index, MessageRow message) {
        write("save a message of ticket #" + ticketId, c -> {
            if (shared()) {
                writeSharedMessage(c, ticketId, message);
            } else {
                writeMessage(c, ticketId, index, message);
            }
        });
    }

    private void writeMessage(Connection c, int ticketId, int index, MessageRow m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p + "ticket_messages "
                + "(ticket_id, seq, author, author_name, staff, text, time, stored) VALUES (?, ?, ?, ?, ?, ?, ?, " + now() + ")")) {
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

    /**
     * MySQL: the message goes to the next free position of the ticket. If another server wrote one at the same moment,
     * the position is taken - then simply the next one.
     */
    private void writeSharedMessage(Connection c, int ticketId, MessageRow m) throws SQLException {
        for (int attempt = 0; ; attempt++) {
            int seq;
            try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(seq), -1) + 1 FROM " + p + "ticket_messages WHERE ticket_id = ?")) {
                ps.setInt(1, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    seq = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + p + "ticket_messages "
                    + "(ticket_id, seq, author, author_name, staff, text, time, stored) VALUES (?, ?, ?, ?, ?, ?, ?, " + now() + ")")) {
                ps.setInt(1, ticketId);
                ps.setInt(2, seq);
                ps.setString(3, m.author().toString());
                ps.setString(4, m.authorName());
                ps.setInt(5, m.staff() ? 1 : 0);
                ps.setString(6, m.text());
                ps.setLong(7, m.time());
                ps.executeUpdate();
                return;
            } catch (SQLException e) {
                boolean taken = e.getSQLState() != null && e.getSQLState().startsWith("23");
                if (!taken || attempt >= 5) {
                    throw e;
                }
            }
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
                    known.remove(id);
                }
            }
        });
    }

    @Override
    public void pollTickets(java.util.function.Consumer<List<LoadedTicket>> changed) {
        if (!shared()) {
            return;
        }
        executor.execute(() -> {
            try (Connection c = pool.getConnection()) {
                long now = dbNow(c);
                long since = lastPoll - POLL_OVERLAP_MILLIS;
                java.util.Set<Integer> ids = new java.util.TreeSet<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM " + p + "tickets WHERE updated >= ?")) {
                    ps.setLong(1, since);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ids.add(rs.getInt(1));
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT ticket_id FROM " + p + "ticket_messages WHERE stored >= ?")) {
                    ps.setLong(1, since);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ids.add(rs.getInt(1));
                        }
                    }
                }
                lastPoll = now;
                if (ids.isEmpty()) {
                    return;
                }
                List<LoadedTicket> result = new ArrayList<>();
                String in = String.join(", ", Collections.nCopies(ids.size(), "?"));
                Map<Integer, List<MessageRow>> messages = new HashMap<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT ticket_id, author, author_name, staff, text, time FROM "
                        + p + "ticket_messages WHERE ticket_id IN (" + in + ") ORDER BY ticket_id, seq")) {
                    int i = 1;
                    for (int id : ids) {
                        ps.setInt(i++, id);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            messages.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(message(rs));
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + p + "tickets WHERE id IN (" + in + ")")) {
                    int i = 1;
                    for (int id : ids) {
                        ps.setInt(i++, id);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            TicketRow row = ticket(rs);
                            known.put(row.id(), row);
                            result.add(new LoadedTicket(row, List.copyOf(messages.getOrDefault(row.id(), List.of()))));
                        }
                    }
                }
                changed.accept(result);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not look for ticket changes of the other servers: " + e.getMessage());
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

    @Override
    public void pruneAudit(long before) {
        write("clean up the web log", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + p + "audit_log WHERE server = ? AND time < ?")) {
                ps.setString(1, server);
                ps.setLong(2, before);
                ps.executeUpdate();
            }
        });
    }

    // ---------------------------------------------------------- health history

    @Override
    public void addHealth(long time, String json) {
        write("save the health history", c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + p + "health (server, time, data) VALUES (?, ?, ?)")) {
                ps.setString(1, server);
                ps.setLong(2, time);
                ps.setString(3, json);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<String> loadHealth(long since) {
        List<String> result = new ArrayList<>();
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT data FROM " + p + "health WHERE server = ? AND time >= ? ORDER BY time")) {
            ps.setString(1, server);
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load the health history: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void pruneHealth(long before) {
        write("clean up the health history", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + p + "health WHERE server = ? AND time < ?")) {
                ps.setString(1, server);
                ps.setLong(2, before);
                ps.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------- proxy operation

    @Override
    public void putServerState(String data) {
        if (!shared()) {
            return;
        }
        write("save the server state", c -> {
            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p + "server_state (server, data, updated) VALUES (?, ?, " + now() + ")")) {
                ps.setString(1, server);
                ps.setString(2, data);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<ServerState> serverStates() {
        List<ServerState> result = new ArrayList<>();
        if (!shared()) {
            return result;
        }
        try (Connection c = pool.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT server, data, " + now() + " - updated FROM " + p + "server_state")) {
            while (rs.next()) {
                result.add(new ServerState(rs.getString(1), rs.getString(2), rs.getLong(3)));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not read the state of the other servers: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void putSwitch(UUID player, String target, long ttlMillis, Runnable done) {
        if (!shared()) {
            return;
        }
        executor.execute(() -> {
            try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p
                    + "proxy_switch (player, target, expires) VALUES (?, ?, " + now() + " + ?)")) {
                ps.setString(1, player.toString());
                ps.setString(2, target);
                ps.setLong(3, ttlMillis);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not save the switch of " + player + ": " + e.getMessage());
                return;
            }
            done.run();
        });
    }

    @Override
    public boolean takeSwitch(UUID player, String target) {
        if (!shared()) {
            return false;
        }
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM " + p
                + "proxy_switch WHERE player = ? AND target = ? AND expires >= " + now())) {
            ps.setString(1, player.toString());
            ps.setString(2, target);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not check the switch of " + player + ": " + e.getMessage());
            return false;
        }
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
        int unused = reserved.getAndSet(0);
        if (unused > 0) {
            // Give the reserved ticket number back - unless another server took a later one meanwhile.
            write("give back ticket number " + unused, c -> {
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + p + "counters SET value = ? WHERE name = ? AND value = ?")) {
                    ps.setLong(1, unused);
                    ps.setString(2, TICKET_COUNTER);
                    ps.setLong(3, unused + 1L);
                    ps.executeUpdate();
                }
            });
        }
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
