package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.api.WebConfig;
import at.marcelr.mradmin.config.ConfigEditor;
import at.marcelr.mradmin.config.ConfigForm;
import at.marcelr.mradmin.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The config of a plugin in the web interface (page Plugins -> plugin -> Configuration), mradmin.config.
 * <pre>
 *  GET  /api/config/{plugin}                     form, values, checksum, worlds
 *  POST /api/config/{plugin} {version, changes}  check, write, reload -> {changed, restart, reloadError}
 * </pre>
 * A value that does not fit answers 400 with {error, field}; a file changed in the meantime 409 "config-changed".
 * Every save goes into the web log with the changed keys (never the values - they may be secrets).
 */
public final class ConfigRoutes {

    private final WebServer server;
    private final MRAdmin plugin;

    private ConfigRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        ConfigRoutes r = new ConfigRoutes(server);
        server.route("GET", "/api/config/{plugin}", r::get);
        server.route("POST", "/api/config/{plugin}", r::save);
    }

    static boolean may(WebServer server, java.util.UUID account) {
        return server.permissions().has(account, Perms.CONFIG);
    }

    private WebConfig target(Http http) throws Exception {
        if (!may(server, http.account().id())) {
            throw new Http.WebException(403, "no-permission");
        }
        String name = http.param("plugin");
        WebConfig target = server.sync(() -> plugin.configEditor().targets().get(name));
        if (target == null) {
            throw new Http.WebException(404, "not-found");
        }
        return target;
    }

    private void get(Http http) throws Exception {
        WebConfig target = target(http);
        ConfigEditor.Loaded loaded = load(target);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plugin", http.param("plugin"));
        result.put("file", "plugins/" + target.file().getParentFile().getName() + "/" + target.file().getName());
        result.put("version", loaded.version());
        result.put("sections", loaded.form().view(loaded.file(), loaded.defaults()));
        result.put("worlds", server.sync(() -> Bukkit.getWorlds().stream().map(World::getName).toList()));
        // Options that belong to another plugin are only shown while it is installed.
        result.put("plugins", java.util.Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(org.bukkit.plugin.Plugin::getName).toList());
        // Live state next to a section (only MRadmin itself: is the starter set up and in use?).
        if (plugin.getName().equals(http.param("plugin"))) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("server", starterStatus());
            if (plugin.settings().requests().application() && !server.requests().mailer().enabled()) {
                status.put("requests", Map.of("tone", "warn", "key", "application-no-mail"));
            }
            result.put("status", status);
        }
        http.json(200, result);
    }

    private ConfigEditor.Loaded load(WebConfig target) throws Exception {
        try {
            return ConfigEditor.load(target);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            // A broken file (edited by hand) or form: say so instead of showing wrong values.
            plugin.getLogger().warning("Cannot show " + target.file() + " in the web interface: " + e.getMessage());
            throw new Http.WebException(409, "config-unreadable");
        }
    }

    private void save(Http http) throws Exception {
        WebConfig target = target(http);
        Map<String, Object> body = http.bodyTree();
        String version = body.get("version") instanceof String v ? v : "";
        if (!(body.get("changes") instanceof Map<?, ?> raw) || raw.isEmpty() || raw.size() > 500) {
            throw new Http.WebException(400, "bad-request");
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        raw.forEach((k, v) -> changes.put(String.valueOf(k), v));
        List<String> changed;
        try {
            changed = ConfigEditor.write(target, version, changes);
        } catch (ConfigEditor.Stale e) {
            throw new Http.WebException(409, "config-changed");
        } catch (ConfigForm.Invalid e) {
            http.json(400, Map.of("error", e.error, "field", e.key));
            return;
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new Http.WebException(409, "config-unreadable");
        }
        String name = http.param("plugin");
        String by = http.account().name() + " (web)";
        String reloadError = null;
        if (!changed.isEmpty()) {
            reloadError = server.sync(() -> {
                try {
                    target.reload().accept(by);
                    return null;
                } catch (Throwable t) {
                    plugin.getLogger().warning("Reloading " + name + " after a web change failed: " + t);
                    return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                }
            });
            plugin.getLogger().info(by + " changed the config of " + name + ": " + String.join(", ", changed));
            plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                    http.storedIp(), "config-saved", name + ": " + String.join(", ", changed)));
        }
        ConfigEditor.Loaded loaded = ConfigEditor.load(target);
        boolean restart = false;
        for (String key : changed) {
            ConfigForm.Field f = loaded.form().field(key);
            if (f == null) {
                // a table cell: key.row.column
                f = loaded.form().sections().stream().flatMap(s -> s.fields().stream())
                        .filter(x -> key.startsWith(x.key() + ".")).findFirst().orElse(null);
            }
            restart |= f != null && f.restart();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changed", changed);
        result.put("restart", restart);
        result.put("reloadError", reloadError);
        result.put("version", loaded.version());
        result.put("sections", loaded.form().view(loaded.file(), loaded.defaults()));
        http.json(200, result);
    }

    /**
     * Section "server" of MRadmin's config: active = the server runs under the starter, ready = the files are there
     * but the server was started another way, none = not set up. The page shows a text for the key.
     */
    private Map<String, Object> starterStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (plugin.starter().active()) {
            String mode = System.getenv("MRADMIN_STARTER_MODE");
            m.put("tone", "good");
            m.put("key", "starter-active");
            m.put("mode", mode == null ? "?" : mode);
        } else if (new java.io.File(Bukkit.getWorldContainer(), "mrserver.properties").isFile()) {
            m.put("tone", "warn");
            m.put("key", "starter-ready");
        } else {
            m.put("tone", "info");
            m.put("key", "starter-none");
        }
        return m;
    }
}
