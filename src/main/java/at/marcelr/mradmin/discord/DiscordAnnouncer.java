package at.marcelr.mradmin.discord;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.util.DurationUtil;
import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Public announcements for the community Discord (own webhook, separate from the admin log). Deliberately sparse:
 * <ul>
 *     <li>every maintenance gets <b>one</b> message that is edited through its states - scheduled (once it is
 *     {@code announce-ahead} away), running, ended or cancelled. Only the first post may ping. The decisions are
 *     made by {@link MaintenanceBoard}.</li>
 *     <li>announcements only for presets with a {@code discord} text (or {@code -d} for own texts), with a cooldown
 *     per preset - a restart script that announces "5 minutes" and "1 minute" results in one post.</li>
 * </ul>
 * Times are Discord timestamps, so every reader sees them in their own timezone.
 */
public final class DiscordAnnouncer {

    private record Config(boolean enabled, URI url, String username, String avatar, String pingRole, String footer,
                          boolean maintenance, MaintenanceBoard.Rules rules, boolean pingMaintenance,
                          boolean announcements, long cooldown, boolean pingAnnouncements,
                          int colorScheduled, int colorStarted, int colorEnded, int colorCancelled, int colorAnnounce,
                          boolean pingNews, boolean pingAlerts, int colorNews, int colorChangelog, int colorAlert) {
        static final Config DISABLED = new Config(false, null, "", "", null, "", false, null, false,
                false, 0, false, 0, 0, 0, 0, 0, false, false, 0, 0, 0);
    }

    private final MRAdmin plugin;
    private final AnnounceWebhook webhook;
    private final MaintenanceBoard board = new MaintenanceBoard();
    private Config config = Config.DISABLED;
    private String disabledReason = "discord-reason-disabled";
    private final Map<String, Instant> lastAnnounce = new HashMap<>();

    public DiscordAnnouncer(MRAdmin plugin) {
        this.plugin = plugin;
        this.webhook = new AnnounceWebhook("MRadmin/" + plugin.getPluginMeta().getVersion(),
                error -> plugin.getLogger().warning("Discord announcement webhook: " + error),
                () -> {
                    // While shutting down the scheduler is gone; shutdown() saves after the last post instead.
                    if (plugin.isEnabled()) {
                        plugin.getServer().getScheduler().runTask(plugin, this::saveNow);
                    }
                });
    }

    // ------------------------------------------------------------------ config

    public void load(ConfigurationSection s) {
        if (s == null || !s.getBoolean("enabled", false)) {
            config = Config.DISABLED;
            disabledReason = "discord-reason-disabled";
            return;
        }
        String raw = s.getString("webhook-url", "").trim();
        if (raw.isEmpty()) {
            disable("discord-reason-no-url");
            return;
        }
        if (!raw.matches("https://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api/webhooks/.+")) {
            disable("discord-reason-bad-url");
            return;
        }
        URI url;
        try {
            url = URI.create(raw);
        } catch (IllegalArgumentException e) {
            disable("discord-reason-invalid-url");
            return;
        }
        String role = s.getString("ping-role-id", "").trim().replaceAll("[<@&>]", "");
        if (!role.isEmpty() && !role.chars().allMatch(Character::isDigit)) {
            plugin.getLogger().warning("discord-announce.ping-role-id '" + role + "' is not a role id (only digits), no ping.");
            role = "";
        }
        MaintenanceBoard.Rules rules = new MaintenanceBoard.Rules(
                Duration.ofMillis(duration(s.getString("maintenance.announce-ahead", "24h"), 86_400_000L)),
                Duration.ofMillis(duration(s.getString("maintenance.min-length", "0"), 0)),
                Duration.ofSeconds(Math.clamp(s.getInt("maintenance.debounce-seconds", 60), 0, 3600)));
        config = new Config(true, url, s.getString("username", ""), s.getString("avatar-url", ""),
                role.isEmpty() ? null : role, s.getString("footer", ""),
                s.getBoolean("maintenance.enabled", true), rules,
                s.getBoolean("maintenance.ping", true),
                s.getBoolean("announcements.enabled", true),
                duration(s.getString("announcements.cooldown", "10m"), 600_000L),
                s.getBoolean("announcements.ping", false),
                color(s.getString("colors.scheduled"), 0xF1C40F), color(s.getString("colors.started"), 0xE74C3C),
                color(s.getString("colors.ended"), 0x2ECC71), color(s.getString("colors.cancelled"), 0x95A5A6),
                color(s.getString("colors.announce"), 0x9B59B6),
                s.getBoolean("news.ping", false), s.getBoolean("alerts.ping", true),
                color(s.getString("colors.news"), 0x3498DB), color(s.getString("colors.changelog"), 0x2ECC71),
                color(s.getString("colors.alert"), 0xE74C3C));
        disabledReason = null;
        plugin.getLogger().info("Discord announcement webhook active.");
    }

    private void disable(String key) {
        config = Config.DISABLED;
        disabledReason = key;
        plugin.getLogger().warning("Discord announcement webhook disabled: " + plugin.messages().console(key));
    }

    public boolean enabled() {
        return config.enabled();
    }

    /** Why it is off (in the reader's language), or null. */
    public String disabledReason() {
        return disabledReason == null ? null : plugin.messages().plain(disabledReason);
    }

    // ------------------------------------------------------------- maintenance

    /**
     * Called every second by the maintenance manager with all known windows.
     *
     * @param kumaStale Uptime Kuma is configured but its list is not current (not connected, or connected but the
     *                  list has not arrived yet). Its maintenances are then left alone - otherwise a reconnect would
     *                  end them and post them again.
     */
    public void update(List<Window> windows, Instant now, boolean kumaStale) {
        Config c = config;
        if (!c.enabled() || !c.maintenance()) {
            return;
        }
        List<MaintenanceBoard.Change> changes = board.update(windows, now, kumaStale, c.rules());
        for (MaintenanceBoard.Change change : changes) {
            MaintenanceBoard.Entry e = change.entry();
            JsonObject payload = payload(c, change.ping() && c.pingMaintenance(), embed(e, c));
            if (change.post()) {
                e.post = webhook.post(c.url(), payload);
            } else {
                webhook.edit(c.url(), e.post, payload);
            }
        }
        if (!changes.isEmpty()) {
            save();
        }
    }

    /** The message of a maintenance in its current state. */
    private JsonObject embed(MaintenanceBoard.Entry e, Config c) {
        String kind = switch (e.status) {
            case SCHEDULED -> "scheduled";
            case RUNNING -> "started";
            case ENDED -> "ended";
            case CANCELLED -> "cancelled";
        };
        int color = switch (e.status) {
            case SCHEDULED -> c.colorScheduled();
            case RUNNING -> c.colorStarted();
            case ENDED -> c.colorEnded();
            case CANCELLED -> c.colorCancelled();
        };
        StringBuilder body = new StringBuilder();
        if (e.title != null && !e.title.isEmpty()) {
            body.append(fill(text("maintenance-topic-line"), e)).append('\n');
        }
        body.append(fill(text("maintenance-" + kind), e));
        if (e.endedEarly()) {
            body.append('\n').append(fill(text("maintenance-ended-early"), e));
        }
        boolean open = e.status == MaintenanceBoard.Status.SCHEDULED || e.status == MaintenanceBoard.Status.RUNNING;
        if (e.changed && open) {
            body.append("\n\n").append(text("maintenance-changed-note"));
        }
        return embed(fill(text("maintenance-" + kind + "-title"), e), body.toString(), color);
    }

    private JsonObject embed(String title, String description, int color) {
        JsonObject embed = new JsonObject();
        if (!title.isEmpty()) {
            embed.addProperty("title", title);
        }
        embed.addProperty("description", description);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        if (!config.footer().isEmpty()) {
            JsonObject footer = new JsonObject();
            footer.addProperty("text", config.footer());
            embed.add("footer", footer);
        }
        return embed;
    }

    private static JsonObject payload(Config c, boolean ping, JsonObject embed) {
        return AnnounceWebhook.payload(c.username(), c.avatar(), ping ? c.pingRole() : null, embed);
    }

    private String text(String key) {
        return plugin.messages().server(() -> plugin.messages().optional("discord-announce." + key));
    }

    /**
     * {title} {start} {start-relative} {end} {end-relative} (planned) and {started} {ended} (actual) - times as
     * Discord timestamps.
     */
    private String fill(String text, MaintenanceBoard.Entry e) {
        String open = text("open-end");
        return text.replace("{title}", escape(e.title == null ? "" : e.title))
                .replace("{start-relative}", e.start == null ? "" : "<t:" + e.start.getEpochSecond() + ":R>")
                .replace("{start}", e.start == null ? open : "<t:" + e.start.getEpochSecond() + ":f>")
                .replace("{end-relative}", e.end == null ? "" : "<t:" + e.end.getEpochSecond() + ":R>")
                .replace("{end}", e.end == null ? open : "<t:" + e.end.getEpochSecond() + ":f>")
                .replace("{started}", e.startedAt == null ? open : "<t:" + e.startedAt.getEpochSecond() + ":f>")
                .replace("{ended}", e.endedAt == null ? open : "<t:" + e.endedAt.getEpochSecond() + ":f>");
    }

    // ----------------------------------------------------------- announcements

    /**
     * Posts an announcement if it is meant for Discord: a preset with a {@code discord} text (not within its
     * cooldown) or an own text sent with {@code -d}.
     *
     * @param preset  preset name, or null for an own text
     * @param title   plain title shown in game
     * @param text    Discord text (preset's {@code discord} text with values filled in, or the plain own text)
     * @return whether it was posted
     */
    public boolean announce(String preset, String title, String text) {
        Config c = config;
        if (!c.enabled() || !c.announcements() || text == null || text.isBlank()) {
            return false;
        }
        String key = preset == null ? "" : preset;
        Instant now = Instant.now();
        Instant last = lastAnnounce.get(key);
        if (preset != null && last != null && now.isBefore(last.plusMillis(c.cooldown()))) {
            return false;
        }
        lastAnnounce.put(key, now);
        webhook.post(c.url(), payload(c, c.pingAnnouncements(), embed(title, text, c.colorAnnounce())));
        return true;
    }


    // -------------------------------------------------------- news, changelogs, warnings

    /**
     * A post of the team (see {@link at.marcelr.mradmin.news.News}) - only if its author asked for it. News and
     * changelogs ping with {@code news.ping}, warnings with {@code alerts.ping}.
     *
     * @param kind  news, changelog or alert (colour)
     * @param title plain title
     * @param body  Discord markdown (escaped where it comes from the post)
     * @param link  the whole post on the player page, or null
     * @return whether it was posted
     */
    public boolean postNews(String kind, String title, String body, String link) {
        Config c = config;
        if (!c.enabled()) {
            return false;
        }
        int color = switch (kind) {
            case "changelog" -> c.colorChangelog();
            case "alert" -> c.colorAlert();
            default -> c.colorNews();
        };
        String text = body;
        if (link != null) {
            text += "\n\n[" + text("news-read-more") + "](" + link + ")";
        }
        boolean ping = "alert".equals(kind) ? c.pingAlerts() : c.pingNews();
        webhook.post(c.url(), payload(c, ping, embed(title, text.length() > 4000 ? text.substring(0, 3999) : text, color)));
        return true;
    }

    /** A text of discord-announce in the server language (for building posts elsewhere). */
    public String label(String key) {
        return text(key);
    }
    public CompletableFuture<String> test(CommandSender sender) {
        Config c = config;
        if (!c.enabled()) {
            return CompletableFuture.completedFuture(plugin.messages().as(sender, this::disabledReason));
        }
        String text = text("test-message");
        return webhook.test(c.url(), payload(c, false, embed("", text.isEmpty() ? "MRadmin" : text, c.colorAnnounce())));
    }

    // ------------------------------------------------------------- persistence

    public void loadState() {
        YamlConfiguration y = plugin.storage().loadYaml("discord-announce", plugin.getLogger());
        try {
            if (y.contains("key")) {
                // Up to 0.6.0: one maintenance with a "scheduled" and a "started" post. Go on with the newer one.
                MaintenanceBoard.Entry e = entry(y, y.getString("key"));
                e.status = "STARTED".equals(y.getString("state")) ? MaintenanceBoard.Status.RUNNING : MaintenanceBoard.Status.SCHEDULED;
                String started = y.getString("started-message", "");
                String id = started.isEmpty() ? y.getString("scheduled-message", "") : started;
                e.post = new AnnounceWebhook.Post(id.isEmpty() ? null : id);
                board.restore(e);
                return;
            }
            ConfigurationSection all = y.getConfigurationSection("maintenances");
            if (all == null) {
                return;
            }
            for (String id : all.getKeys(false)) {
                ConfigurationSection s = all.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                MaintenanceBoard.Entry e = entry(s, s.getString("key"));
                e.status = MaintenanceBoard.Status.valueOf(s.getString("status", "SCHEDULED"));
                e.startedAt = instant(s.getString("started-at", ""));
                e.changed = s.getBoolean("changed");
                String message = s.getString("message", "");
                e.post = new AnnounceWebhook.Post(message.isEmpty() ? null : message);
                board.restore(e);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("discord-announce.yml is unreadable, starting fresh: " + e.getMessage());
        }
    }

    private static MaintenanceBoard.Entry entry(ConfigurationSection s, String key) {
        MaintenanceBoard.Entry e = new MaintenanceBoard.Entry(key);
        e.title = s.getString("title", "");
        e.start = instant(s.getString("start", ""));
        e.end = instant(s.getString("end", ""));
        return e;
    }

    /** Message ids arrive a moment after posting - the webhook saves again once they are there (onPosted). */
    private void save() {
        saveNow();
    }

    private void saveNow() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - maintenance posted to the announcement webhook (managed by the plugin)."));
        int i = 0;
        for (MaintenanceBoard.Entry e : board.entries()) {
            String path = "maintenances.m" + i++ + ".";
            y.set(path + "key", e.key);
            y.set(path + "title", e.title);
            y.set(path + "status", e.status.name());
            y.set(path + "start", e.start == null ? "" : e.start.toString());
            y.set(path + "end", e.end == null ? "" : e.end.toString());
            y.set(path + "started-at", e.startedAt == null ? "" : e.startedAt.toString());
            y.set(path + "changed", e.changed);
            y.set(path + "message", e.post == null || e.post.id == null ? "" : e.post.id);
        }
        plugin.storage().saveYaml("discord-announce", y);
    }

    public void shutdown() {
        // First let pending posts finish, so their message ids are known when saving.
        webhook.shutdown();
        saveNow();
    }

    // ------------------------------------------------------------------ helpers

    private static Instant instant(String raw) {
        return raw == null || raw.isEmpty() ? null : Instant.parse(raw);
    }

    private long duration(String raw, long fallback) {
        long ms = DurationUtil.parse(raw);
        return ms < 0 ? fallback : ms;
    }

    private static int color(String raw, int fallback) {
        try {
            return raw == null ? fallback : Integer.parseInt(raw.replace("#", "").trim(), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Topics come from Uptime Kuma or commands - they must not break the Markdown or mention anyone. */
    public static String escape(String value) {
        return value.replaceAll("([_*~`|>\\\\])", "\\\\$1").replace("@", "@​");
    }

    /** For "/mradmin discord": the maintenance currently posted about, e.g. "kuma:3 (scheduled)", or null. */
    public String trackedKey() {
        String keys = board.entries().stream()
                .map(e -> e.key + " (" + e.status.name().toLowerCase(Locale.ROOT) + ")")
                .collect(Collectors.joining(", "));
        return keys.isEmpty() ? null : keys;
    }
}
