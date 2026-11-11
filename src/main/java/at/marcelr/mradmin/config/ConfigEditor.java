package at.marcelr.mradmin.config;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.WebConfig;
import at.marcelr.mradmin.api.WebModule;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The config files the web interface may edit: MRadmin's own (once it has a {@code web-config.yml}) and those of the
 * modules that offer one ({@link WebModule#webConfig()}, API 12), by plugin name.
 * <p>
 * Saving: the file is read again, its checksum must still be the one the browser loaded (otherwise someone changed it
 * in the meantime), the changes are checked against the form, the old file goes to {@code config.backup-web.yml}, the
 * new one is written in one step (UTF-8 with BOM, comments stay) and the plugin reloads it on the main thread.
 */
public final class ConfigEditor {

    private static final String BOM = "﻿";

    private final MRAdmin plugin;

    public ConfigEditor(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** Main thread: the editable configs by plugin name. */
    public Map<String, WebConfig> targets() {
        Map<String, WebConfig> result = new LinkedHashMap<>();
        WebConfig own = own();
        if (own != null) {
            result.put(plugin.getName(), own);
        }
        for (AdminModule module : plugin.modules().all()) {
            if (!module.plugin().isEnabled()) {
                continue;
            }
            WebModule web = plugin.modules().call(module, module::web, null);
            WebConfig config = web == null ? null : plugin.modules().call(module, web::webConfig, null);
            if (config != null && config.file() != null && config.form() != null) {
                result.put(module.plugin().getName(), config);
            }
        }
        // Plugins on the waiting room server: their mirror (saving becomes an order, network.RemotePlugins).
        if (plugin.waitingRoom().active()) {
            plugin.waitingRoom().remote().webConfigs().forEach(result::putIfAbsent);
        }
        return result;
    }

    /** MRadmin's own config, if its form is bundled. */
    private WebConfig own() {
        String form = resource("web-config.yml");
        String defaults = resource("config.yml");
        if (form == null || defaults == null) {
            return null;
        }
        return new WebConfig(new File(plugin.getDataFolder(), "config.yml"), form, defaults, by -> {
            String[] problem = plugin.reload();
            if (problem == null) {
                plugin.discord().log("admin-reload", "admin", by);
            } else {
                throw new IllegalStateException("line " + problem[1] + ": " + problem[0]);
            }
        });
    }

    private String resource(String name) {
        try (InputStream in = plugin.getResource(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------- reading and writing (any thread)

    /** A config as read from disk: its form, the file, the bundled defaults and the checksum of the file. */
    public record Loaded(ConfigForm form, YamlConfiguration file, YamlConfiguration defaults, String version) {
    }

    public static Loaded load(WebConfig target) throws IOException, InvalidConfigurationException {
        String text = target.file().isFile() ? Files.readString(target.file().toPath(), StandardCharsets.UTF_8) : "";
        YamlConfiguration file = new YamlConfiguration();
        file.loadFromString(ConfigForm.strip(text));
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString(ConfigForm.strip(target.defaults()));
        return new Loaded(ConfigForm.parse(target.form()), file, defaults, checksum(text));
    }

    /**
     * Checks and writes the changes (not the reload).
     *
     * @return the keys that changed (empty = nothing written)
     * @throws Stale          the file changed since the browser loaded it
     * @throws ConfigForm.Invalid a value does not fit
     */
    public static synchronized List<String> write(WebConfig target, String version, Map<String, Object> changes)
            throws IOException, InvalidConfigurationException, ConfigForm.Invalid, Stale {
        Loaded loaded = load(target);
        if (!loaded.version().equals(version)) {
            throw new Stale();
        }
        List<String> changed = loaded.form().apply(loaded.file(), loaded.defaults(), changes);
        if (changed.isEmpty()) {
            return changed;
        }
        loaded.file().options().width(10_000);
        Path path = target.file().toPath();
        Files.createDirectories(path.getParent());
        if (Files.isRegularFile(path)) {
            Files.copy(path, path.resolveSibling("config.backup-web.yml"), StandardCopyOption.REPLACE_EXISTING);
        }
        Path temp = path.resolveSibling(path.getFileName() + ".web-tmp");
        Files.writeString(temp, BOM + loaded.file().saveToString(), StandardCharsets.UTF_8);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return changed;
    }

    /** The file changed since it was loaded. */
    public static final class Stale extends Exception {
        Stale() {
            super("config changed");
        }
    }

    static String checksum(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
