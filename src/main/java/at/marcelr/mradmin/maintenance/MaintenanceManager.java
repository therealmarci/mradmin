package at.marcelr.mradmin.maintenance;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.player.Npcs;
import com.google.gson.JsonObject;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Maintenance state machine, evaluated every second on the main thread:
 * <ul>
 *     <li>{@link Phase#PLANNED} - a maintenance is known but further away than {@code warn-before}</li>
 *     <li>{@link Phase#UPCOMING} - countdown bossbar, reminders and title countdown for everyone</li>
 *     <li>{@link Phase#ACTIVE} - only players with {@link #PERM_BYPASS} may join; the others are kicked</li>
 * </ul>
 * Windows come from Uptime Kuma ({@link KumaClient}) and from commands (one manual window). Every change of the
 * chosen window (moved, extended, shortened, cancelled) is detected by comparing it with the last tick.
 * The announced state is stored in {@code data.yml}, so a restart neither repeats nor skips announcements.
 */
public final class MaintenanceManager {

    public static final String PERM_BYPASS = at.marcelr.mradmin.Perms.MAINTENANCE_BYPASS;
    public static final String PERM_MANAGE = at.marcelr.mradmin.Perms.MAINTENANCE_MANAGE;
    public static final String PERM_NOTIFY = at.marcelr.mradmin.Perms.MAINTENANCE_NOTIFY;

    public enum Phase { NONE, PLANNED, UPCOMING, ACTIVE }

    private final MRAdmin plugin;
    private final KumaClient kuma;

    private List<Window> kumaWindows = List.of();
    private Window manual;
    /** Kuma maintenances ended early with "/mradmin maintenance off": key -> start they had at that time. */
    private final Map<String, Instant> ignored = new HashMap<>();

    // State of the last tick (persisted).
    private Phase phase = Phase.NONE;
    private Window tracked;

    private long lastRemaining = Long.MAX_VALUE;
    private long lastTitleSecond = -1;
    private boolean kumaWasConnected;
    private volatile boolean active;
    private volatile Window activeWindow;

    /** Boss bars per player - every player reads them in their own language. */
    private final Map<UUID, BossBar> countdownBars = new HashMap<>();
    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private BukkitTask task;

    public MaintenanceManager(MRAdmin plugin) {
        this.plugin = plugin;
        this.kuma = new KumaClient(plugin, this::onKumaList, this::onKumaConnection);
    }

    public void start() {
        stop();
        if (!plugin.feature(at.marcelr.mradmin.Feature.MAINTENANCE)) {
            // Switched off: nobody is kept out, no countdown, no Kuma connection. Plans stay in maintenance.yml.
            phase = Phase.NONE;
            tracked = null;
            active = false;
            activeWindow = null;
            return;
        }
        kuma.start(plugin.settings().kuma());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        tick();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        kuma.stop();
        hideBars();
    }

    // ----------------------------------------------------------------- input

    private void onKumaList(List<JsonObject> maintenances) {
        Instant now = Instant.now();
        List<Window> windows = new ArrayList<>();
        for (JsonObject m : maintenances) {
            Window window = KumaParser.parse(m, now, plugin.settings().zone());
            if (window != null) {
                windows.add(window);
            }
        }
        kumaWindows = List.copyOf(windows);
        // Forget "ended early" marks whose maintenance is gone or has a new time.
        ignored.entrySet().removeIf(e -> kumaWindows.stream()
                .noneMatch(w -> w.key().equals(e.getKey()) && Objects.equals(w.start(), e.getValue())));
        save();
        tick();
    }

    private void onKumaConnection(boolean connected) {
        if (connected) {
            if (!kumaWasConnected) {
                plugin.discord().log("kuma-connected", "url", plugin.settings().kuma().url());
            }
            kumaWasConnected = true;
            return;
        }
        if (kumaWasConnected) {
            plugin.discord().log("kuma-disconnected", "url", plugin.settings().kuma().url());
            messages().broadcastTo(PERM_NOTIFY, "kuma-lost");
        }
        kumaWasConnected = false;
        if (!plugin.settings().kuma().keepWhenOffline()) {
            kumaWindows = List.of();
            save();
            tick();
        }
    }

    /** All windows that count: Kuma (minus the ones ended early) and the manual one. */
    public List<Window> windows() {
        List<Window> all = new ArrayList<>();
        for (Window w : kumaWindows) {
            if (!(ignored.containsKey(w.key()) && Objects.equals(ignored.get(w.key()), w.start()))) {
                all.add(w);
            }
        }
        if (manual != null) {
            all.add(manual);
        }
        return all;
    }

    // ------------------------------------------------------------- commands

    /** Starts a manual maintenance now; {@code duration} null = until "off". */
    public void startNow(Duration duration, String title, String by) {
        Instant now = Instant.now();
        manual = new Window(Window.MANUAL, 0, title, now, duration == null ? null : now.plus(duration), false);
        save();
        tick();
        plugin.getLogger().info(by + " started a maintenance" + (duration == null ? "." : " for " + Texts.duration(duration.toMillis()) + "."));
    }

    /** Plans a manual maintenance in {@code in} from now (with countdown). */
    public void schedule(Duration in, Duration duration, String title, String by) {
        Instant start = Instant.now().plus(in);
        manual = new Window(Window.MANUAL, 0, title, start, duration == null ? null : start.plus(duration), false);
        save();
        tick();
        plugin.getLogger().info(by + " scheduled a maintenance in " + Texts.duration(in.toMillis()) + ".");
    }

    /** Extends (or shortens, if negative) the running or next maintenance's end; manual ones only. */
    public boolean extendManual(Duration by) {
        if (manual == null) {
            return false;
        }
        Instant base = manual.end() != null ? manual.end() : Instant.now();
        Instant end = base.plus(by);
        if (manual.start() != null && !end.isAfter(manual.start())) {
            return false;
        }
        manual = new Window(manual.source(), manual.id(), manual.title(), manual.start(), end, manual.running());
        save();
        tick();
        return true;
    }

    /**
     * Ends the current maintenance or cancels the planned one: the manual window is removed, a Kuma window is
     * ignored until Uptime Kuma reports a new time for it.
     *
     * @return whether there was something to end
     */
    public boolean stopCurrent(String by) {
        Window current = Planner.choose(windows(), Instant.now());
        if (current == null) {
            return false;
        }
        if (current.source().equals(Window.MANUAL)) {
            manual = null;
        } else {
            for (Window w : kumaWindows) {
                if (w.activeAt(Instant.now()) || w.key().equals(current.key())) {
                    ignored.put(w.key(), w.start());
                }
            }
        }
        save();
        tick();
        plugin.getLogger().info(by + " ended/cancelled the maintenance.");
        return true;
    }

    public void refreshKuma() {
        kuma.refresh();
    }

    // ------------------------------------------------------------------ tick

    /**
     * Logs and Discord in the server language, even when a command of some player triggered the tick; everything that
     * reaches players is built per player in their language.
     */
    private void tick() {
        messages().server(() -> {
            evaluate();
            return null;
        });
    }

    private void evaluate() {
        Instant now = Instant.now();
        Settings.MaintenanceSettings m = plugin.settings().maintenance();
        if (manual != null && manual.end() != null && !now.isBefore(manual.end())) {
            manual = null;
            save();
        }
        Window current = Planner.choose(windows(), now);
        Phase next = phaseOf(current, now, m);
        Window previous = tracked;
        Phase before = phase;

        boolean sameKey = previous != null && current != null && previous.key().equals(current.key());
        boolean startChanged = sameKey && !Objects.equals(previous.start(), current.start());
        boolean endChanged = sameKey && !Objects.equals(previous.end(), current.end());

        // ---- transitions
        if (before == Phase.ACTIVE && next != Phase.ACTIVE) {
            ended(previous);
        }
        if (next == Phase.ACTIVE && before != Phase.ACTIVE) {
            started(current);
        } else if (next == Phase.ACTIVE && endChanged) {
            extended(previous, current);
        } else if (next == Phase.UPCOMING && before != Phase.UPCOMING) {
            announce(current, now);
        } else if (next == Phase.UPCOMING && (startChanged || !sameKey)) {
            messages().broadcast("maintenance-rescheduled", resolvers(current, now));
        } else if (before == Phase.UPCOMING && next == Phase.PLANNED) {
            messages().broadcast("maintenance-rescheduled", resolvers(current, now));
        } else if ((before == Phase.UPCOMING || before == Phase.PLANNED) && next == Phase.NONE) {
            if (before == Phase.UPCOMING) {
                messages().broadcast("maintenance-cancelled", resolvers(previous, now));
            }
            plugin.discord().log("maintenance-cancelled", discordTags(previous));
        }
        // Discord: a new maintenance or new times (not for the countdown phases themselves).
        if (current != null && next != Phase.ACTIVE && before != Phase.ACTIVE) {
            if (previous == null || !sameKey) {
                plugin.discord().log("maintenance-scheduled", discordTags(current));
            } else if (startChanged || endChanged) {
                plugin.discord().log("maintenance-changed", discordTags(current));
            }
        }

        // ---- countdown extras
        if (next == Phase.UPCOMING) {
            long remaining = current.start().toEpochMilli() - now.toEpochMilli();
            if (before == Phase.UPCOMING && sameKey && !startChanged) {
                for (long reminder : m.reminders()) {
                    if (lastRemaining > reminder && remaining <= reminder) {
                        messages().broadcast("maintenance-reminder", resolvers(current, now));
                        playSound(m);
                        break;
                    }
                }
            }
            long second = (remaining + 999) / 1000;
            if (second <= m.titleCountdownSeconds() && second > 0 && second != lastTitleSecond) {
                lastTitleSecond = second;
                TagResolver[] tags = resolvers(current, now);
                messages().each(Npcs.online(), p -> ((Player) p).showTitle(Title.title(
                        messages().get("maintenance-countdown-title", tag("seconds", second)),
                        messages().get("maintenance-countdown-subtitle", tags),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(300)))));
                playSound(m);
            }
            lastRemaining = remaining;
        } else {
            lastRemaining = Long.MAX_VALUE;
            lastTitleSecond = -1;
        }

        boolean changed = before != next || !Objects.equals(previous, current);
        phase = next;
        tracked = current;
        active = next == Phase.ACTIVE;
        activeWindow = active ? current : null;
        if (changed) {
            save();
        }
        updateBars(current, now, m);
        plugin.discordAnnounce().update(current, windows(), now,
                plugin.settings().kuma().enabled() && kuma.state() != KumaClient.State.CONNECTED);
    }

    private static Phase phaseOf(Window window, Instant now, Settings.MaintenanceSettings m) {
        if (window == null) {
            return Phase.NONE;
        }
        if (window.activeAt(now)) {
            return Phase.ACTIVE;
        }
        long remaining = window.start().toEpochMilli() - now.toEpochMilli();
        return remaining <= m.warnBefore() ? Phase.UPCOMING : Phase.PLANNED;
    }

    private void announce(Window window, Instant now) {
        messages().broadcast("maintenance-announced", resolvers(window, now));
        playSound(plugin.settings().maintenance());
        plugin.getLogger().info("Maintenance starts at " + window.start() + (window.end() == null ? "" : " and ends at " + window.end()) + ".");
        plugin.push("maintenance", "maintenance-soon", "/maintenance", null, "title", title(window),
                "start", Texts.at(plugin, window.start()));
    }

    /** For push notifications: the window's title, or the default one. */
    private String title(Window window) {
        return window.title() == null || window.title().isBlank()
                ? messages().plain("push-maintenance-default") : window.title();
    }

    private void started(Window window) {
        Instant now = Instant.now();
        plugin.push("maintenance", "maintenance-start", "/maintenance", null, "title", title(window),
                "end", window.end() == null ? messages().plain("push-open-end") : Texts.at(plugin, window.end()));
        Settings.MaintenanceSettings m = plugin.settings().maintenance();
        messages().broadcast("maintenance-started", resolvers(window, now));
        TagResolver[] tags = resolvers(window, now);
        messages().each(Npcs.online(), p -> ((Player) p).showTitle(Title.title(
                messages().get("maintenance-start-title", tags), messages().get("maintenance-start-subtitle", tags))));
        playSound(m);
        plugin.discord().log("maintenance-start", discordTags(window));
        plugin.getLogger().info("Maintenance started" + (window.end() == null ? " (open end)." : ", ends at " + window.end() + "."));
        if (m.kickOnStart()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active) {
                    return;
                }
                for (Player player : List.copyOf(Npcs.online())) {
                    if (!canJoin(player)) {
                        player.kick(messages().as(player, this::kickScreen));
                    }
                }
            }, m.kickDelaySeconds() * 20L);
        }
    }

    private void extended(Window before, Window after) {
        Instant now = Instant.now();
        String key = before.end() == null || (after.end() != null && after.end().isAfter(before.end()))
                ? "maintenance-extended" : "maintenance-shortened";
        messages().broadcastTo(PERM_BYPASS, key, resolvers(after, now));
        plugin.discord().log("maintenance-changed", discordTags(after));
        plugin.getLogger().info("Maintenance end changed to " + (after.end() == null ? "open end" : after.end()) + ".");
    }

    private void ended(Window window) {
        messages().broadcast("maintenance-ended");
        plugin.discord().log("maintenance-end", discordTags(window));
        plugin.getLogger().info("Maintenance ended.");
    }

    private void playSound(Settings.MaintenanceSettings m) {
        if (m.sound() == null) {
            return;
        }
        Sound sound = Sound.sound(m.sound(), Sound.Source.MASTER, 1f, 1f);
        Npcs.online().forEach(p -> p.playSound(sound, Sound.Emitter.self()));
    }

    // ---------------------------------------------------------------- bossbar

    private void updateBars(Window window, Instant now, Settings.MaintenanceSettings m) {
        Map<UUID, BossBar> countdown = new HashMap<>();
        Map<UUID, BossBar> during = new HashMap<>();
        if (m.bossbar() && window != null) {
            TagResolver[] tags = resolvers(window, now);
            if (phase == Phase.UPCOMING) {
                long remaining = window.start().toEpochMilli() - now.toEpochMilli();
                float progress = clamp(m.warnBefore() <= 0 ? 0 : (float) remaining / m.warnBefore());
                BossBar.Color color = remaining <= m.finalSeconds() * 1000L ? m.finalColor() : m.color();
                for (Player player : Npcs.online()) {
                    countdown.put(player.getUniqueId(), bar(countdownBars, player, "maintenance-bossbar", tags,
                            progress, color, m.overlay()));
                }
            } else if (phase == Phase.ACTIVE && m.showDuring()) {
                float progress = 1f;
                if (window.end() != null && window.start() != null) {
                    long total = window.end().toEpochMilli() - window.start().toEpochMilli();
                    long remaining = window.end().toEpochMilli() - now.toEpochMilli();
                    progress = clamp(total <= 0 ? 0 : (float) remaining / total);
                }
                String key = window.end() == null ? "maintenance-bossbar-active-open" : "maintenance-bossbar-active";
                for (Player player : Npcs.online()) {
                    if (player.hasPermission(PERM_BYPASS)) {
                        during.put(player.getUniqueId(), bar(activeBars, player, key, tags, progress, m.duringColor(),
                                m.overlay()));
                    }
                }
            }
        }
        sync(countdownBars, countdown);
        sync(activeBars, during);
    }

    /** The player's bar of this kind, created if needed and updated with the text in their language. */
    private BossBar bar(Map<UUID, BossBar> bars, Player player, String key, TagResolver[] tags, float progress,
                        BossBar.Color color, BossBar.Overlay overlay) {
        Component name = messages().as(player, () -> messages().get(key, tags));
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> BossBar.bossBar(name, progress, color, overlay));
        bar.name(name);
        bar.progress(progress);
        bar.color(color);
        bar.overlay(overlay);
        return bar;
    }

    /** Shows the wanted bars and hides (and forgets) all others of this kind. */
    private static void sync(Map<UUID, BossBar> shown, Map<UUID, BossBar> wanted) {
        for (Map.Entry<UUID, BossBar> entry : Map.copyOf(shown).entrySet()) {
            if (!wanted.containsKey(entry.getKey())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    player.hideBossBar(entry.getValue());
                }
                shown.remove(entry.getKey());
            }
        }
        for (Map.Entry<UUID, BossBar> entry : wanted.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.showBossBar(entry.getValue());
            }
        }
    }

    private void hideBars() {
        sync(countdownBars, Map.of());
        sync(activeBars, Map.of());
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    // ----------------------------------------------------------------- access

    /** Whether only staff may join right now (thread-safe, also used on the login thread). */
    public boolean active() {
        return active;
    }

    public Phase phase() {
        return phase;
    }

    /** The running or next maintenance, or null. */
    public Window current() {
        return tracked;
    }

    public Window manual() {
        return manual;
    }

    public KumaClient kuma() {
        return kuma;
    }

    public boolean canJoin(Player player) {
        return player.hasPermission(PERM_BYPASS);
    }

    /** Text on the kick / "cannot join" screen. */
    public Component kickScreen() {
        Window window = activeWindow;
        return messages().get("maintenance-kick", resolvers(window, Instant.now()));
    }

    /**
     * Placeholders of a window: title, start, end, remaining time until start / end. {@code <title-line>} (chat) and
     * {@code <kick-title-line>} (kick screen) are a whole line with the topic, or nothing if there is no topic.
     * While running with an open end, {@code <countdown>} and {@code <remaining>} show time-open-end.
     */
    public TagResolver[] resolvers(Window window, Instant now) {
        Supplier<String> open = () -> Texts.at(plugin, null);
        if (window == null) {
            return new TagResolver[]{tag("title", ""), Messages.lazy("start", open), Messages.lazy("end", open),
                    tag("in", "0s"), tag("countdown", "0:00:00"), tag("remaining", "0s"),
                    tag("title-line", Component.empty()), tag("kick-title-line", Component.empty())};
        }
        long untilStart = window.start() == null ? 0 : Math.max(0, window.start().toEpochMilli() - now.toEpochMilli());
        long untilEnd = window.end() == null ? 0 : Math.max(0, window.end().toEpochMilli() - now.toEpochMilli());
        boolean running = window.activeAt(now);
        boolean openEnd = window.end() == null;
        String title = window.title() == null ? "" : window.title();
        return new TagResolver[]{
                tag("title", title),
                Messages.lazy("title-line", () -> title.isEmpty() ? Component.empty()
                        : messages().get("maintenance-title-line", tag("title", title))),
                Messages.lazy("kick-title-line", () -> title.isEmpty() ? Component.empty()
                        : messages().get("maintenance-kick-title-line", tag("title", title))),
                Messages.lazy("start", () -> Texts.at(plugin, window.start())),
                Messages.lazy("end", () -> Texts.at(plugin, window.end())),
                tag("in", Texts.duration(untilStart)),
                Messages.lazy("countdown", () -> running && openEnd ? open.get() : Texts.clock(running ? untilEnd : untilStart)),
                Messages.lazy("remaining", () -> openEnd ? open.get() : Texts.duration(untilEnd))};
    }

    /** Whether the running maintenance has no known end (manual one without duration, Kuma "manual" strategy). */
    public boolean openEnd() {
        Window window = activeWindow;
        return window != null && window.end() == null;
    }

    private Object[] discordTags(Window window) {
        if (window == null) {
            return new Object[0];
        }
        return new Object[]{"title", window.title() == null || window.title().isEmpty() ? "-" : window.title(),
                "start", Texts.at(plugin, window.start()), "end", Texts.at(plugin, window.end()),
                "source", window.source().equals(Window.KUMA) ? "Uptime Kuma (#" + window.id() + ")" : messages().plain("maintenance-source-manual")};
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ----------------------------------------------------------- persistence

    public void load() {
        YamlConfiguration data = plugin.storage().loadYaml("maintenance", plugin.getLogger());
        manual = readWindow(data.getConfigurationSection("manual"));
        List<Window> cached = new ArrayList<>();
        ConfigurationSection kumaSection = data.getConfigurationSection("kuma-cache");
        if (kumaSection != null) {
            for (String key : kumaSection.getKeys(false)) {
                Window w = readWindow(kumaSection.getConfigurationSection(key));
                if (w != null) {
                    cached.add(w);
                }
            }
        }
        kumaWindows = List.copyOf(cached);
        ConfigurationSection ignoredSection = data.getConfigurationSection("ignored");
        if (ignoredSection != null) {
            for (String key : ignoredSection.getKeys(false)) {
                String start = ignoredSection.getString(key, "");
                ignored.put(key.replace('_', ':'), start.isEmpty() ? null : Instant.parse(start));
            }
        }
        try {
            phase = Phase.valueOf(data.getString("state.phase", "NONE"));
        } catch (IllegalArgumentException e) {
            phase = Phase.NONE;
        }
        tracked = readWindow(data.getConfigurationSection("state.window"));
        active = phase == Phase.ACTIVE;
        activeWindow = active ? tracked : null;
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.options().setHeader(List.of("MRadmin - state of maintenances (managed by the plugin, do not edit)."));
        writeWindow(data, "manual", manual);
        int i = 0;
        for (Window w : kumaWindows) {
            writeWindow(data, "kuma-cache.w" + i++, w);
        }
        ignored.forEach((key, start) -> data.set("ignored." + key.replace(':', '_'), start == null ? "" : start.toString()));
        data.set("state.phase", phase.name());
        writeWindow(data, "state.window", tracked);
        plugin.storage().saveYaml("maintenance", data);
    }

    private static void writeWindow(YamlConfiguration data, String path, Window w) {
        if (w == null) {
            return;
        }
        data.set(path + ".source", w.source());
        data.set(path + ".id", w.id());
        data.set(path + ".title", w.title());
        data.set(path + ".start", w.start() == null ? "" : w.start().toString());
        data.set(path + ".end", w.end() == null ? "" : w.end().toString());
        data.set(path + ".running", w.running());
    }

    private static Window readWindow(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        try {
            String start = s.getString("start", "");
            String end = s.getString("end", "");
            return new Window(s.getString("source", Window.MANUAL), s.getInt("id"), s.getString("title", ""),
                    start.isEmpty() ? null : Instant.parse(start), end.isEmpty() ? null : Instant.parse(end),
                    s.getBoolean("running"));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
