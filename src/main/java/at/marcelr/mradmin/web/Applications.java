package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.WebRequest;
import at.marcelr.mradmin.web.auth.Secrets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Whitelist applications from the start page (widget "Whitelist application", address /bewerben): name, Minecraft
 * name and e-mail. The address is confirmed by a link first - only then the application reaches the team, as a ticket
 * ({@link WebRequest#APPLICATION}) with "accept" and "reject" in it. Accepted: the player goes on the server's
 * whitelist and can make an account in the web interface right away (status link), without having been in game.
 * <p>
 * Applications waiting for the confirmation are kept in the storage file "applications-pending" (24 hours).
 */
public final class Applications {

    private static final String FILE = "applications-pending";
    private static final long CONFIRM_MILLIS = 24L * 60 * 60 * 1000;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** An application before the address was confirmed. */
    private record Pending(String name, String mcName, UUID mcUuid, String email, long created) {
    }

    /** A Minecraft name as the server knows players: exact spelling and the UUID the whitelist uses. */
    public record Profile(String name, UUID uuid) {
    }

    private final MRAdmin plugin;
    private final WebServer server;
    /** hash of the confirmation code -> application */
    private final Map<String, Pending> pending = new HashMap<>();
    private boolean loaded;

    Applications(MRAdmin plugin, WebServer server) {
        this.plugin = plugin;
        this.server = server;
    }

    /** Switched on, tickets on and mail set up (the answer goes out by mail). */
    public boolean available() {
        return plugin.settings().requests().application() && plugin.feature(at.marcelr.mradmin.Feature.TICKETS)
                && server.requests().mailer().enabled();
    }

    /**
     * The player behind a name: online-mode servers (or behind an online-mode proxy) ask Mojang - an unknown name is
     * refused; offline-mode servers use the offline UUID like the server does. Web thread (may download).
     * @return the profile, or null if Mojang does not know the name
     */
    Profile resolve(String name) throws Exception {
        boolean online = server.sync(Applications::onlineMode);
        if (!online) {
            UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return new Profile(name, offline);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || response.statusCode() == 204) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new Http.WebException(503, "mojang-unavailable");
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String id = json.get("id").getAsString();
        UUID uuid = UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        return new Profile(json.get("name").getAsString(), uuid);
    }

    private static boolean onlineMode() {
        if (Bukkit.getOnlineMode()) {
            return true;
        }
        // Behind a proxy the proxy checks the accounts (Velocity with online-mode, BungeeCord) - real UUIDs then.
        YamlConfiguration paper = YamlConfiguration.loadConfiguration(new java.io.File("config/paper-global.yml"));
        YamlConfiguration spigot = YamlConfiguration.loadConfiguration(new java.io.File("spigot.yml"));
        return (paper.getBoolean("proxies.velocity.enabled", false) && paper.getBoolean("proxies.velocity.online-mode", true))
                || spigot.getBoolean("settings.bungeecord", false);
    }

    /** Whether the player is on the whitelist already. Server thread. */
    boolean whitelisted(UUID uuid) {
        return Bukkit.getOfflinePlayer(uuid).isWhitelisted();
    }

    /** An open application ticket of this player, or null. Server thread. */
    Ticket openFor(UUID uuid) {
        return plugin.tickets().byCreator(uuid).stream()
                .filter(t -> t.open() && t.request() != null && t.request().application())
                .findFirst().orElse(null);
    }

    /** A new application waiting for the confirmation link; another unconfirmed one of the same player is replaced. @return the code */
    synchronized String add(String name, Profile profile, String email) {
        load();
        long now = System.currentTimeMillis();
        pending.values().removeIf(p -> p.created() < now - CONFIRM_MILLIS || p.mcUuid().equals(profile.uuid()));
        String code = Secrets.token(24);
        pending.put(Secrets.sha256(code), new Pending(name, profile.name(), profile.uuid(),
                email.trim().toLowerCase(Locale.ROOT), now));
        save();
        return code;
    }

    /** The confirmation link was opened: the application becomes a ticket. @return its status token, or null if the link is invalid */
    String confirm(String code, java.util.function.Supplier<String> newToken) throws Exception {
        Pending p;
        synchronized (this) {
            load();
            p = code == null ? null : pending.remove(Secrets.sha256(code));
            save();
        }
        if (p == null || p.created() < System.currentTimeMillis() - CONFIRM_MILLIS) {
            return null;
        }
        String token = newToken.get();
        WebRequest request = WebRequest.application(p.email(), token, p.mcName(), p.mcUuid().toString());
        var m = plugin.messages();
        String subject = m.server(() -> m.plain("request-application-subject", "player", p.mcName()));
        String text = m.server(() -> m.plain("request-application-text", "name", p.name(), "player", p.mcName(),
                "email", p.email(), "time", Texts.at(plugin, Instant.ofEpochMilli(p.created()))));
        Ticket ticket = server.sync(() -> {
            Ticket open = openFor(p.mcUuid());
            if (open != null) {
                return open;
            }
            return plugin.tickets().createRequest(request, p.mcUuid(), p.mcName(), subject, text, null);
        });
        if (!token.equals(ticket.request().token())) {
            return ticket.request().token();
        }
        server.requests().sendReceived(ticket);
        return token;
    }

    /** Puts the player on the whitelist (the server's own command - it also knows the name for whitelist.json). Server thread. */
    void whitelist(String mcName) {
        if (!PublicRequests.validName(mcName)) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:whitelist add " + mcName);
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        YamlConfiguration y = plugin.storage().loadYaml(FILE, plugin.getLogger());
        ConfigurationSection section = y.getConfigurationSection("pending");
        if (section == null) {
            return;
        }
        for (String hash : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(hash);
            if (s == null) {
                continue;
            }
            try {
                pending.put(hash, new Pending(s.getString("name", ""), s.getString("player", ""),
                        UUID.fromString(s.getString("uuid", "")), s.getString("email", ""), s.getLong("created")));
            } catch (IllegalArgumentException ignored) {
                // broken entry
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - whitelist applications waiting for the e-mail confirmation (managed by the plugin)."));
        for (Map.Entry<String, Pending> e : pending.entrySet()) {
            String base = "pending." + e.getKey() + ".";
            y.set(base + "name", e.getValue().name());
            y.set(base + "player", e.getValue().mcName());
            y.set(base + "uuid", e.getValue().mcUuid().toString());
            y.set(base + "email", e.getValue().email());
            y.set(base + "created", e.getValue().created());
        }
        plugin.storage().saveYaml(FILE, y);
    }
}
