package at.marcelr.mradmin.discord;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.util.DurationUtil;
import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Public announcements for the community Discord (own webhook, separate from the admin log). Deliberately sparse:
 * <ul>
 *     <li>a maintenance gets at most three posts - <b>scheduled</b> (once it is {@code announce-ahead} away),
 *     <b>started</b> and <b>ended</b>. Moving, extending or cancelling edits the latest of them instead of posting
 *     again; several changes in a row are combined ({@code debounce-seconds}).</li>
 *     <li>announcements only for presets with a {@code discord} text (or {@code -d} for own texts), with a cooldown
 *     per preset - a restart script that announces "5 minutes" and "1 minute" results in one post.</li>
 * </ul>
 * Times are Discord timestamps, so every reader sees them in their own timezone.
 */
public final class DiscordAnnouncer {

    private enum State { SCHEDULED, STARTED }

    private record Config(boolean enabled, URI url, String username, String avatar, String pingRole, String footer,
                          boolean maintenance, long announceAhead, long minLength, boolean postStart, boolean postEnd,
                          boolean pingMaintenance, int debounceSeconds,
                          boolean announcements, long cooldown, boolean pingAnnouncements,
                          int colorScheduled, int colorStarted, int colorEnded, int colorCancelled, int colorAnnounce) {
        static final Config DISABLED = new Config(false, null, "", "", null, "", false, 0, 0, false, false, false, 0,
                false, 0, false, 0, 0, 0, 0, 0);
    }

    /** The maintenance that has been posted about. */
    private static final class Track {
        String key;
        String title;
        Instant start;
        Instant end;
        State state;
        AnnounceWebhook.Post scheduledPost;
        AnnounceWebhook.Post startedPost;
        /** Times seen but not yet edited into the post (debounce). */
        Instant dirtySince;
    }

    private final MRAdmin plugin;
    private final AnnounceWebhook webhook;
    private Config config = Config.DISABLED;
    private String disabledReason = "discord-reason-disabled";
    private Track track;
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
        config = new Config(true, url, s.getString("username", ""), s.getString("avatar-url", ""),
                role.isEmpty() ? null : role, s.getString("footer", ""),
                s.getBoolean("maintenance.enabled", true),
                duration(s.getString("maintenance.announce-ahead", "24h"), 86_400_000L),
                duration(s.getString("maintenance.min-length", "0"), 0),
                s.getBoolean("maintenance.post-start", true),
                s.getBoolean("maintenance.post-end", true),
                s.getBoolean("maintenance.ping", true),
                Math.clamp(s.getInt("maintenance.debounce-seconds", 60), 0, 3600),
                s.getBoolean("announcements.enabled", true),
                duration(s.getString("announcements.cooldown", "10m"), 600_000L),
                s.getBoolean("announcements.ping", false),
                color(s.getString("colors.scheduled"), 0xF1C40F), color(s.getString("colors.started"), 0xE74C3C),
                color(s.getString("colors.ended"), 0x2ECC71), color(s.getString("colors.cancelled"), 0x95A5A6),
                color(s.getString("colors.announce"), 0x9B59B6));
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
     * Called every second by the maintenance manager with the chosen window and all windows.
     *
     * @param kumaOffline Uptime Kuma is configured but not connected right now. A posted Kuma maintenance is then
     *                    left alone - otherwise a Kuma restart would post "cancelled" and then "scheduled" again.
     */
    public void update(Window current, List<Window> windows, Instant now, boolean kumaOffline) {
        Config c = config;
        if (!c.enabled() || !c.maintenance()) {
            return;
        }
        if (track != null) {
            Track t = track;
            if (kumaOffline && t.key.startsWith(Window.KUMA + ":")) {
                return;
            }
            Window same = current != null && current.key().equals(t.key) ? current
                    : windows.stream().filter(w -> w.key().equals(t.key)).findFirst().orElse(null);
            if (t.state == State.STARTED && (same == null || !same.activeAt(now))) {
                ended(t);
                return;
            }
            if (t.state == State.SCHEDULED && same == null) {
                cancelled(t);
                return;
            }
            if (t.state == State.SCHEDULED && same.activeAt(now)) {
                started(t, same, false);
                return;
            }
            if (!java.util.Objects.equals(same.start(), t.start) || !java.util.Objects.equals(same.end(), t.end)) {
                if (t.dirtySince == null) {
                    t.dirtySince = now;
                }
                if (!now.isBefore(t.dirtySince.plusSeconds(c.debounceSeconds()))) {
                    t.start = same.start();
                    t.end = same.end();
                    t.title = same.title();
                    t.dirtySince = null;
                    editLatest(t, true);
                    save();
                }
            } else {
                t.dirtySince = null;
            }
            return;
        }
        if (current == null || !longEnough(current, c)) {
            return;
        }
        if (current.activeAt(now)) {
            Track t = newTrack(current);
            started(t, current, true);
        } else if (current.start() != null && current.start().toEpochMilli() - now.toEpochMilli() <= c.announceAhead()) {
            Track t = newTrack(current);
            t.state = State.SCHEDULED;
            t.scheduledPost = webhook.post(c.url(), payload(c, c.pingMaintenance(), embed("scheduled", t, c.colorScheduled(), false)));
            track = t;
            save();
        }
    }

    private static Track newTrack(Window w) {
        Track t = new Track();
        t.key = w.key();
        t.title = w.title();
        t.start = w.start();
        t.end = w.end();
        return t;
    }

    private static boolean longEnough(Window w, Config c) {
        if (c.minLength() <= 0 || w.end() == null || w.start() == null) {
            return true;
        }
        return w.end().toEpochMilli() - w.start().toEpochMilli() >= c.minLength();
    }

    /** @param first true if nothing was posted about this maintenance yet (then the post may ping) */
    private void started(Track t, Window w, boolean first) {
        Config c = config;
        t.start = w.start();
        t.end = w.end();
        t.title = w.title();
        t.state = State.STARTED;
        t.dirtySince = null;
        if (c.postStart()) {
            t.startedPost = webhook.post(c.url(), payload(c, first && c.pingMaintenance(), embed("started", t, c.colorStarted(), false)));
        } else if (t.scheduledPost != null) {
            webhook.edit(c.url(), t.scheduledPost, payload(c, false, embed("started", t, c.colorStarted(), false)));
        }
        track = t;
        save();
    }

    private void ended(Track t) {
        Config c = config;
        if (c.postEnd()) {
            webhook.post(c.url(), payload(c, false, embed("ended", t, c.colorEnded(), false)));
        }
        track = null;
        save();
    }

    private void cancelled(Track t) {
        Config c = config;
        webhook.edit(c.url(), t.scheduledPost, payload(c, false, embed("cancelled", t, c.colorCancelled(), false)));
        track = null;
        save();
    }

    /** Puts the new times into the latest post (started post if there is one, otherwise the scheduled one). */
    private void editLatest(Track t, boolean changed) {
        Config c = config;
        if (t.state == State.STARTED && t.startedPost != null) {
            webhook.edit(c.url(), t.startedPost, payload(c, false, embed("started", t, c.colorStarted(), changed)));
        } else if (t.scheduledPost != null) {
            String kind = t.state == State.STARTED ? "started" : "scheduled";
            int color = t.state == State.STARTED ? c.colorStarted() : c.colorScheduled();
            webhook.edit(c.url(), t.scheduledPost, payload(c, false, embed(kind, t, color, changed)));
        }
    }

    private JsonObject embed(String kind, Track t, int color, boolean changed) {
        String title = t.title == null ? "" : t.title;
        String description = fill(text("maintenance-" + kind), t);
        String topic = title.isEmpty() ? "" : fill(text("maintenance-topic-line"), t);
        StringBuilder body = new StringBuilder();
        if (!topic.isEmpty()) {
            body.append(topic).append('\n');
        }
        body.append(description);
        if (changed) {
            body.append("\n\n").append(text("maintenance-changed-note"));
        }
        return embed(fill(text("maintenance-" + kind + "-title"), t), body.toString(), color);
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
        return plugin.messages().optional("discord-announce." + key);
    }

    /** {title} {start} {start-relative} {end} {end-relative} - times as Discord timestamps. */
    private String fill(String text, Track t) {
        String open = text("open-end");
        return text.replace("{title}", escape(t.title == null ? "" : t.title))
                .replace("{start-relative}", t.start == null ? "" : "<t:" + t.start.getEpochSecond() + ":R>")
                .replace("{start}", t.start == null ? open : "<t:" + t.start.getEpochSecond() + ":f>")
                .replace("{end-relative}", t.end == null ? "" : "<t:" + t.end.getEpochSecond() + ":R>")
                .replace("{end}", t.end == null ? open : "<t:" + t.end.getEpochSecond() + ":f>");
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
        if (!y.contains("key")) {
            return;
        }
        try {
            Track t = new Track();
            t.key = y.getString("key");
            t.title = y.getString("title", "");
            t.start = instant(y.getString("start", ""));
            t.end = instant(y.getString("end", ""));
            t.state = State.valueOf(y.getString("state", "SCHEDULED"));
            String scheduled = y.getString("scheduled-message", "");
            String started = y.getString("started-message", "");
            t.scheduledPost = scheduled.isEmpty() ? null : new AnnounceWebhook.Post(scheduled);
            t.startedPost = started.isEmpty() ? null : new AnnounceWebhook.Post(started);
            track = t;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("discord-announce.yml is unreadable, starting fresh: " + e.getMessage());
        }
    }

    /** Message ids arrive a moment after posting - the webhook saves again once they are there (onPosted). */
    private void save() {
        saveNow();
    }

    private void saveNow() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - last maintenance posted to the announcement webhook (managed by the plugin)."));
        Track t = track;
        if (t != null) {
            y.set("key", t.key);
            y.set("title", t.title);
            y.set("start", t.start == null ? "" : t.start.toString());
            y.set("end", t.end == null ? "" : t.end.toString());
            y.set("state", t.state.name());
            y.set("scheduled-message", t.scheduledPost == null || t.scheduledPost.id == null ? "" : t.scheduledPost.id);
            y.set("started-message", t.startedPost == null || t.startedPost.id == null ? "" : t.startedPost.id);
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
        Track t = track;
        return t == null ? null : t.key + " (" + t.state.name().toLowerCase(java.util.Locale.ROOT) + ")";
    }
}
