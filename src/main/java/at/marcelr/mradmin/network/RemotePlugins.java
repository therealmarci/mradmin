package at.marcelr.mradmin.network;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.api.WebConfig;
import at.marcelr.mradmin.versions.ReleasePicker;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plugins on a connected server (the waiting room) that MRadmin manages from afar - one direction only: the remote
 * plugin reports itself every few seconds ({@code POST /waiting-room/report}: name, version, its config form, its
 * config.yml and the bundled defaults, which orders it carried out) and picks up orders with its status question
 * ({@code GET /waiting-room/status}): take this config.yml, install this release.
 * <ul>
 *     <li><b>Configuration:</b> MRadmin keeps a mirror of the remote config.yml in
 *     {@code plugins/MRadmin/remote/<plugin>/config.yml}; the normal config editor edits it like any other
 *     ({@link #webConfigs()}). Saving turns the new file into an order. While an order is open, the mirror is not
 *     overwritten by older reports.</li>
 *     <li><b>Updates:</b> the plugin shows up on the page Plugins ({@code VersionManager}); "update" becomes an order
 *     with the official release address and its SHA-256 - the remote plugin downloads and checks it itself, MRadmin
 *     never sends a JAR.</li>
 * </ul>
 * Everything here is thread-safe; reports arrive on the link thread.
 */
public final class RemotePlugins {

    /** A report counts as current this long (the remote plugin reports every 30 seconds). */
    private static final long ONLINE_MILLIS = 90_000L;
    private static final int MAX_TEXT = 256 * 1024;
    private static final Gson GSON = new Gson();

    /**
     * What a remote plugin last reported.
     *
     * @param staged version waiting for the remote server's next start, or null
     * @param host   the remote server itself (starter, Paper build, running since, next planned restart, what blocks a
     *               restart) - shown on the tile "waiting room server"
     */
    public record Report(String name, String version, String commit, String branch, String repository, String form,
                         String defaults, String config, String staged, String server, Map<String, Object> host,
                         long at) {

        public boolean online() {
            return System.currentTimeMillis() - at < ONLINE_MILLIS;
        }
    }

    /** An open order (until the remote plugin reports it as done or failed). */
    private record Order(String id, String plugin, Map<String, Object> json) {
    }

    private final MRAdmin plugin;
    private final Map<String, Report> reports = new LinkedHashMap<>();
    private final Map<String, Order> orders = new LinkedHashMap<>();
    /** Last failed order per plugin (shown on the page), or none. */
    private final Map<String, String> errors = new LinkedHashMap<>();

    public RemotePlugins(MRAdmin plugin) {
        this.plugin = plugin;
    }

    public synchronized Report report(String name) {
        return reports.get(name);
    }

    public synchronized String error(String name) {
        return errors.get(name);
    }

    /** Whether an order for the plugin is still open (e.g. "update requested"). */
    public synchronized String openOrder(String name, String type) {
        for (Order o : orders.values()) {
            if (o.plugin().equals(name) && type.equals(o.json().get("type"))) {
                return String.valueOf(o.json().getOrDefault("version", "1"));
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ reports

    /**
     * A report from the link (link thread).
     *
     * @return null if accepted, otherwise why not
     */
    public String receive(String body) {
        if (body.length() > 2 * MAX_TEXT) {
            return "too-large";
        }
        JsonObject o;
        try {
            o = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            return "json";
        }
        String name = str(o, "name");
        if (!name.matches("MR[A-Za-z0-9]{1,30}")) {
            return "name";
        }
        String repository = str(o, "repository");
        if (!repository.matches("therealmarci/[a-z0-9-]{1,40}")) {
            return "repository";
        }
        Report report = new Report(name, str(o, "version"), str(o, "commit"), str(o, "branch"), repository,
                text(o, "form"), text(o, "defaults"), text(o, "config"), str(o, "staged").isEmpty() ? null : str(o, "staged"),
                str(o, "server"), host(o), System.currentTimeMillis());
        boolean changed;
        synchronized (this) {
            Report old = reports.put(name, report);
            changed = old == null || !old.version().equals(report.version()) || !java.util.Objects.equals(old.staged(), report.staged());
            acknowledge(o);
            // It restarts right now: an open restart order is done (it must not be taken again after the start).
            if (Boolean.TRUE.equals(report.host().get("restarting"))) {
                plugin.getLogger().info("The waiting room server (" + name + ") restarts - restarts of this server wait until it is back.");
                orders.values().removeIf(order -> order.plugin().equals(name) && "restart".equals(order.json().get("type")));
            }
            if (!report.config().isEmpty() && openOrder(name, "config") == null) {
                mirror(report);
            }
        }
        if (changed) {
            // New or other version: show it on the page Plugins and look up what is newer.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.versions() != null) {
                    plugin.versions().discover();
                    plugin.versions().check(null);
                }
            });
        }
        return null;
    }

    /** Orders the remote plugin reports as done or failed. */
    private void acknowledge(JsonObject o) {
        if (o.get("done") instanceof JsonArray done) {
            for (JsonElement e : done) {
                Order order = orders.remove(e.getAsString());
                if (order != null) {
                    errors.remove(order.plugin());
                    plugin.getLogger().info(order.plugin() + " on the waiting room server carried out: " + order.json().get("type")
                            + (order.json().containsKey("version") ? " " + order.json().get("version") : "") + ".");
                }
            }
        }
        if (o.get("failed") instanceof JsonArray failed) {
            for (JsonElement e : failed) {
                if (!(e instanceof JsonObject f)) {
                    continue;
                }
                Order order = orders.remove(str(f, "id"));
                if (order != null) {
                    String error = str(f, "error");
                    errors.put(order.plugin(), error);
                    plugin.getLogger().warning(order.plugin() + " on the waiting room server could not carry out "
                            + order.json().get("type") + ": " + error);
                }
            }
        }
    }

    // --------------------------------------------------------------------- orders

    /** The open orders as JSON (for the status answer). */
    public synchronized String ordersJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Order o : orders.values()) {
            list.add(o.json());
        }
        return GSON.toJson(list);
    }

    /** Take this config.yml (the mirror after saving in the editor). */
    public synchronized void orderConfig(String name, String text, String by) {
        orders.values().removeIf(o -> o.plugin().equals(name) && "config".equals(o.json().get("type")));
        Map<String, Object> json = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();
        json.put("id", id);
        json.put("plugin", name);
        json.put("type", "config");
        json.put("text", text);
        orders.put(id, new Order(id, name, json));
        plugin.getLogger().info((by == null ? "Someone" : by) + " changed the configuration of " + name
                + " on the waiting room server - it takes it on its next question.");
    }

    /**
     * Install this release (downloaded and checked by the remote plugin itself).
     *
     * @return null if ordered, otherwise an error key
     */
    public synchronized String orderUpdate(String name, ReleasePicker.Release release, String by) {
        Report report = reports.get(name);
        if (report == null || !report.online()) {
            return "remote-offline";
        }
        if (release == null || release.sha256() == null) {
            return "versions-no-checksum";
        }
        if (!release.download().startsWith("https://github.com/" + report.repository() + "/releases/download/")) {
            return "versions-no-release";
        }
        orders.values().removeIf(o -> o.plugin().equals(name) && "update".equals(o.json().get("type")));
        Map<String, Object> json = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();
        json.put("id", id);
        json.put("plugin", name);
        json.put("type", "update");
        json.put("version", release.version());
        json.put("url", release.download());
        json.put("sha256", release.sha256());
        orders.put(id, new Order(id, name, json));
        plugin.getLogger().info((by == null ? "Automatic update" : by) + ": " + name + " " + release.version()
                + " ordered for the waiting room server.");
        return null;
    }

    // ------------------------------------------------------------- configuration

    /** Remote configs for the config editor, by plugin name (only those that reported a form). */
    public synchronized Map<String, WebConfig> webConfigs() {
        Map<String, WebConfig> result = new LinkedHashMap<>();
        for (Report r : reports.values()) {
            if (r.form().isEmpty() || r.defaults().isEmpty() || !mirrorFile(r.name()).isFile()) {
                continue;
            }
            String name = r.name();
            result.put(name, new WebConfig(mirrorFile(name), r.form(), r.defaults(), by -> {
                try {
                    orderConfig(name, Files.readString(mirrorFile(name).toPath(), StandardCharsets.UTF_8), by);
                } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage());
                }
            }));
        }
        return result;
    }

    private File mirrorFile(String name) {
        return new File(plugin.getDataFolder(), "remote/" + name + "/config.yml");
    }

    /** Writes the reported config.yml as mirror (only if it differs, so the editor's checksum stays). */
    private void mirror(Report r) {
        Path path = mirrorFile(r.name()).toPath();
        try {
            if (Files.isRegularFile(path) && Files.readString(path, StandardCharsets.UTF_8).equals(r.config())) {
                return;
            }
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling("config.yml.tmp");
            Files.writeString(temp, r.config(), StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not keep the configuration of " + r.name() + ": " + e.getMessage());
        }
    }

    /**
     * Restart the remote server (it checks itself whether it may - maintenance, restarts of this server, waiting
     * players - and reports back if not).
     *
     * @return null if ordered, otherwise an error key
     */
    public synchronized String orderRestart(String name, String by) {
        Report report = reports.get(name);
        if (report == null || !report.online()) {
            return "remote-offline";
        }
        orders.values().removeIf(o -> o.plugin().equals(name) && "restart".equals(o.json().get("type")));
        errors.remove(name);
        Map<String, Object> json = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();
        json.put("id", id);
        json.put("plugin", name);
        json.put("type", "restart");
        json.put("by", by == null ? "MRadmin" : by);
        orders.put(id, new Order(id, name, json));
        plugin.getLogger().info((by == null ? "MRadmin" : by) + " asked the waiting room server (" + name + ") to restart.");
        return null;
    }

    /**
     * A team member goes to the waiting room on purpose (admin menu): it must not send them back by itself. Expires
     * after a minute if the waiting room does not pick it up.
     */
    public synchronized void orderVisit(String name, java.util.UUID player) {
        long now = System.currentTimeMillis();
        orders.values().removeIf(o -> "visit".equals(o.json().get("type"))
                && (now - ((Number) o.json().get("at")).longValue() > 60_000L || player.toString().equals(o.json().get("player"))));
        Map<String, Object> json = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();
        json.put("id", id);
        json.put("plugin", name);
        json.put("type", "visit");
        json.put("player", player.toString());
        json.put("at", now);
        orders.put(id, new Order(id, name, json));
    }

    // -------------------------------------------------------------------- helpers

    /** The block "host" of a report as plain values (numbers, texts, booleans). */
    private static Map<String, Object> host(JsonObject o) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (o.get("host") instanceof JsonObject h) {
            for (String key : h.keySet()) {
                JsonElement v = h.get(key);
                if (key.matches("[A-Za-z]{1,30}") && v != null && v.isJsonPrimitive()) {
                    var p = v.getAsJsonPrimitive();
                    result.put(key, p.isBoolean() ? p.getAsBoolean() : p.isNumber() ? p.getAsLong()
                            : p.getAsString().length() > 100 ? p.getAsString().substring(0, 100) : p.getAsString());
                }
                if (result.size() >= 20) {
                    break;
                }
            }
        }
        return Map.copyOf(result);
    }

    private static String str(JsonObject o, String key) {
        try {
            String s = o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
            s = s.replaceAll("[\\r\\n\\t]+", " ").strip();
            return s.length() > 200 ? s.substring(0, 200) : s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String text(JsonObject o, String key) {
        try {
            String s = o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
            return s.length() > MAX_TEXT ? "" : s;
        } catch (RuntimeException e) {
            return "";
        }
    }
}
