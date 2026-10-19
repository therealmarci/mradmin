package at.marcelr.mradmin.storage;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * The files MRadmin always used: {@code <name>.yml} per document and {@code tickets.yml} for all tickets.
 * <p>
 * tickets.yml holds everything in one file, so it is rewritten as a whole - but on the storage thread and at most
 * every half second, no longer on the server thread for every message. The storage thread keeps its own copy of the
 * tickets for that.
 */
final class YamlStorage implements Storage {

    private static final String TICKETS = "tickets.yml";
    private static final long WRITE_DELAY_MS = 500;

    private final MRAdmin plugin;
    private final File folder;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "MRadmin-Storage");
        thread.setDaemon(true);
        return thread;
    });

    // Only touched on the storage thread (after loadTickets).
    private final Map<Integer, TicketRow> tickets = new TreeMap<>();
    private final Map<Integer, List<MessageRow>> messages = new TreeMap<>();
    private int nextId = 1;
    private boolean writeScheduled;

    YamlStorage(MRAdmin plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder();
    }

    @Override
    public String type() {
        return "yaml";
    }

    @Override
    public String describe() {
        return "plugins/" + folder.getName() + "/*.yml";
    }

    /** The data files: *.yml and *.log in the folder and below, without config, backups, migrated files and lang. */
    @Override
    public long sizeBytes() {
        try (var files = Files.walk(folder.toPath())) {
            return files.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                String relative = folder.toPath().relativize(path).toString().replace('\\', '/');
                return (name.endsWith(".yml") || name.endsWith(".log")) && !relative.startsWith("lang/")
                        && !name.startsWith("config") && !name.endsWith(".migrated.yml");
            }).mapToLong(path -> path.toFile().length()).sum();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------- documents

    @Override
    public String loadState(String name) {
        File file = new File(folder, name + ".yml");
        if (!file.exists()) {
            return null;
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void saveState(String name, String yaml) {
        executor.execute(() -> write(new File(folder, name + ".yml"), yaml));
    }

    // --------------------------------------------------------------- tickets

    @Override
    public Tickets loadTickets() {
        Tickets loaded = read(new File(folder, TICKETS), plugin);
        // Hand the copy over to the storage thread, which owns it from now on.
        executor.execute(() -> {
            tickets.clear();
            messages.clear();
            for (LoadedTicket t : loaded.tickets()) {
                tickets.put(t.ticket().id(), t.ticket());
                messages.put(t.ticket().id(), new ArrayList<>(t.messages()));
            }
            nextId = loaded.nextId();
        });
        return loaded;
    }

    @Override
    public void saveTicket(TicketRow ticket, int nextId) {
        executor.execute(() -> {
            tickets.put(ticket.id(), ticket);
            messages.computeIfAbsent(ticket.id(), k -> new ArrayList<>());
            this.nextId = Math.max(this.nextId, nextId);
            scheduleWrite();
        });
    }

    @Override
    public void addMessage(int ticketId, int index, MessageRow message) {
        executor.execute(() -> {
            List<MessageRow> list = messages.computeIfAbsent(ticketId, k -> new ArrayList<>());
            if (index >= list.size()) {
                list.add(message);
            } else {
                list.set(index, message);
            }
            scheduleWrite();
        });
    }

    @Override
    public void deleteTickets(Collection<Integer> ids) {
        List<Integer> copy = List.copyOf(ids);
        executor.execute(() -> {
            copy.forEach(id -> {
                tickets.remove(id);
                messages.remove(id);
            });
            scheduleWrite();
        });
    }

    /** Many changes in a row (a conversation, a cleanup) end up in one write. */
    private void scheduleWrite() {
        if (!writeScheduled) {
            writeScheduled = true;
            executor.schedule(this::writeTickets, WRITE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void writeTickets() {
        writeScheduled = false;
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - support tickets (managed by the plugin, do not edit while the server runs)."));
        y.set("next-id", nextId);
        for (TicketRow t : tickets.values()) {
            String p = "tickets." + t.id() + ".";
            y.set(p + "creator", t.creator().toString());
            y.set(p + "creator-name", t.creatorName());
            if (t.target() != null) {
                y.set(p + "target", t.target().toString());
                y.set(p + "target-name", t.targetName());
            }
            y.set(p + "category", t.category());
            y.set(p + "subject", t.subject());
            y.set(p + "time", Instant.ofEpochMilli(t.created()).toString());
            y.set(p + "world", t.world());
            y.set(p + "x", t.x());
            y.set(p + "y", t.y());
            y.set(p + "z", t.z());
            if (t.claimedBy() != null) {
                y.set(p + "claimed-by", t.claimedBy().toString());
                y.set(p + "claimed-by-name", t.claimedByName());
            }
            if (t.closedBy() != null) {
                y.set(p + "closed-by", t.closedBy());
                y.set(p + "closed-at", Instant.ofEpochMilli(t.closedAt()).toString());
            }
            y.set(p + "creator-unread", t.creatorUnread());
            y.set(p + "staff-unread", t.staffUnread());
            if (t.source() != null) {
                y.set(p + "source", t.source());
                y.set(p + "source-id", t.sourceId());
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (MessageRow m : messages.getOrDefault(t.id(), List.of())) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("author", m.author().toString());
                map.put("name", m.authorName());
                map.put("staff", m.staff());
                map.put("time", Instant.ofEpochMilli(m.time()).toString());
                map.put("text", m.text());
                list.add(map);
            }
            y.set(p + "messages", list);
        }
        write(new File(folder, TICKETS), y.saveToString());
    }

    /** Reads tickets.yml (also used to move it into a database). Broken tickets are skipped with a warning. */
    static Tickets read(File file, MRAdmin plugin) {
        if (!file.exists()) {
            return new Tickets(List.of(), 1);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        int next = Math.max(1, y.getInt("next-id", 1));
        List<LoadedTicket> result = new ArrayList<>();
        ConfigurationSection section = y.getConfigurationSection("tickets");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    LoadedTicket ticket = readTicket(Integer.parseInt(key), section.getConfigurationSection(key));
                    result.add(ticket);
                    next = Math.max(next, ticket.ticket().id() + 1);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning(file.getName() + ": ticket " + key + " is broken, skipped (" + e.getMessage() + ").");
                }
            }
        }
        return new Tickets(List.copyOf(result), next);
    }

    private static LoadedTicket readTicket(int id, ConfigurationSection t) {
        String target = t.getString("target", "");
        String claimed = t.getString("claimed-by", "");
        String closedBy = t.getString("closed-by", "");
        String closedAt = t.getString("closed-at", "");
        String source = t.getString("source", "");
        // Before free categories, tickets of the types "bug" and "other" had no category: the type was it.
        TicketRow row = new TicketRow(id, UUID.fromString(t.getString("creator")), t.getString("creator-name", "?"),
                target.isEmpty() ? null : UUID.fromString(target), t.getString("target-name"),
                t.getString("category", t.getString("type", "other")), t.getString("subject", ""),
                Instant.parse(t.getString("time")).toEpochMilli(), t.getString("world", ""),
                t.getDouble("x"), t.getDouble("y"), t.getDouble("z"),
                claimed.isEmpty() ? null : UUID.fromString(claimed), claimed.isEmpty() ? null : t.getString("claimed-by-name", "?"),
                closedBy.isEmpty() ? null : closedBy,
                closedBy.isEmpty() ? 0 : closedAt.isEmpty() ? System.currentTimeMillis() : Instant.parse(closedAt).toEpochMilli(),
                t.getBoolean("creator-unread", false), t.getBoolean("staff-unread", false),
                source.isEmpty() ? null : source, source.isEmpty() ? null : t.getString("source-id", ""));
        List<MessageRow> list = new ArrayList<>();
        for (Map<?, ?> m : t.getMapList("messages")) {
            list.add(new MessageRow(UUID.fromString(String.valueOf(m.get("author"))), String.valueOf(m.get("name")),
                    Boolean.parseBoolean(String.valueOf(m.get("staff"))), String.valueOf(m.get("text")),
                    Instant.parse(String.valueOf(m.get("time"))).toEpochMilli()));
        }
        return new LoadedTicket(row, List.copyOf(list));
    }

    // ------------------------------------------------------------- audit log

    private static final String AUDIT = "web-audit.log";
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    /** One JSON object per line, appended - the file is only ever read from the end. */
    @Override
    public void audit(AuditRow row) {
        String line = GSON.toJson(row) + "\n";
        executor.execute(() -> {
            try {
                Files.writeString(new File(folder, AUDIT).toPath(), line, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write " + AUDIT + ": " + e.getMessage());
            }
        });
    }

    @Override
    public List<AuditRow> recentAudit(int limit) {
        File file = new File(folder, AUDIT);
        if (!file.exists()) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<AuditRow> result = new ArrayList<>();
            for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
                try {
                    result.add(GSON.fromJson(lines.get(i), AuditRow.class));
                } catch (RuntimeException ignored) {
                    // a line cut off by a crash
                }
            }
            return result;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + AUDIT + ": " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public void pruneAudit(long before) {
        executor.execute(() -> {
            File file = new File(folder, AUDIT);
            if (!file.exists()) {
                return;
            }
            try {
                List<String> keep = new ArrayList<>();
                int removed = 0;
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    AuditRow row;
                    try {
                        row = GSON.fromJson(line, AuditRow.class);
                    } catch (RuntimeException e) {
                        row = null;
                    }
                    if (row != null && row.time() >= before) {
                        keep.add(line);
                    } else {
                        removed++;
                    }
                }
                if (removed > 0) {
                    write(file, keep.isEmpty() ? "" : String.join("\n", keep) + "\n");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not clean up " + AUDIT + ": " + e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------- health history

    private static final String HEALTH = "health.log";

    /** One JSON object per line (with its "time"), like the web log. */
    @Override
    public void addHealth(long time, String json) {
        String line = json + "\n";
        executor.execute(() -> {
            try {
                Files.writeString(new File(folder, HEALTH).toPath(), line, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write " + HEALTH + ": " + e.getMessage());
            }
        });
    }

    @Override
    public List<String> loadHealth(long since) {
        File file = new File(folder, HEALTH);
        if (!file.exists()) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank() && time(line) >= since) {
                    result.add(line);
                }
            }
            return result;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + HEALTH + ": " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public void pruneHealth(long before) {
        executor.execute(() -> {
            File file = new File(folder, HEALTH);
            if (!file.exists()) {
                return;
            }
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                List<String> keep = lines.stream().filter(l -> !l.isBlank() && time(l) >= before).toList();
                if (keep.size() < lines.size()) {
                    write(file, keep.isEmpty() ? "" : String.join("\n", keep) + "\n");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not clean up " + HEALTH + ": " + e.getMessage());
            }
        });
    }

    /** The "time" of a JSON line, 0 if it is broken (a line cut off by a crash). */
    private static long time(String line) {
        try {
            return com.google.gson.JsonParser.parseString(line).getAsJsonObject().get("time").getAsLong();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Writes to a temporary file first and then replaces the old one, so a crash never leaves half a file. */
    private void write(File file, String content) {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            Files.writeString(temp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + file.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
        executor.execute(() -> {
            if (writeScheduled) {
                writeTickets();
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Saving took too long - some changes may be lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
