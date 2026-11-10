package at.marcelr.mradmin.notify;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.player.Npcs;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.web.push.PushService;
import at.marcelr.mradmin.web.push.PushService.Channel;
import at.marcelr.mradmin.web.push.PushService.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Team notices: one place for everything the team should know about - a new ticket, a whitelist application, a crash,
 * an update waiting for the restart, the kinds of the modules (a locked chunk loader ...). Each notice goes, as each
 * member chose per kind in their account ({@link PushService#choices}), to
 * <ul>
 *   <li>the <b>inbox</b> of the web interface (kept 60 days, read and archived like the player notifications),</li>
 *   <li><b>push</b> on their devices,</li>
 *   <li>the <b>game</b>: one short chat line with the details in the hover and a link to the page - several of the
 *       same kind within a few seconds as one line, players in a duel only after it, nothing while muted
 *       ({@code /mradmin mute}). Kinds with their own message (tickets, lag) only follow the choice and the mute.</li>
 * </ul>
 * A notice is "done" when its reason is gone (the ticket was claimed or closed, the update is installed ...): it stays
 * in the inbox, grey, and no longer counts as unread. Kinds switched off in config.yml ({@code notifications.disabled})
 * go nowhere. Texts are kept in every loaded language, built from "push-&lt;text&gt;-title/-body".
 */
public final class TeamNotices implements Listener {

    /** One notice. {@code to} = members who get it in the inbox; {@code texts} = language -> [title, body]. */
    public static final class Notice {
        final long id;
        final String kind;
        final String ref;
        final String url;
        final long time;
        final Map<String, String[]> texts;
        final Set<UUID> to;
        volatile boolean done;

        Notice(long id, String kind, String ref, String url, long time, Map<String, String[]> texts, Set<UUID> to, boolean done) {
            this.id = id;
            this.kind = kind;
            this.ref = ref;
            this.url = url;
            this.time = time;
            this.texts = texts;
            this.to = to;
            this.done = done;
        }

        public long id() {
            return id;
        }

        public String kind() {
            return kind;
        }

        public String url() {
            return url;
        }

        public long time() {
            return time;
        }

        public String title(String lang) {
            return text(lang)[0];
        }

        public String body(String lang) {
            return text(lang)[1];
        }

        private String[] text(String lang) {
            String[] t = texts.get(lang);
            if (t == null) {
                t = texts.get("en");
            }
            return t != null ? t : texts.values().stream().findFirst().orElse(new String[]{"", ""});
        }
    }

    private static final String FILE = "team-notices";
    private static final long MAX_AGE = 60L * 24 * 60 * 60 * 1000;
    private static final int MAX_NOTICES = 1000;
    /** A line in the game waits this long for more of the same kind. */
    private static final long GAME_GATHER_MILLIS = 3000;

    private record Pending(Notice notice, long at) {
    }

    private final MRAdmin plugin;
    private final List<Notice> notices = new ArrayList<>();
    private final Map<UUID, Long> mutes = new HashMap<>();
    private final Map<UUID, List<Pending>> pending = new HashMap<>();
    private long nextId = 1;
    private BukkitTask task;
    private volatile ToIntFunction<UUID> unread = id -> 0;

    public TeamNotices(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private PushService push() {
        return plugin.web().push();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ----------------------------------------------------------------- life

    public void start() {
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::flushGame, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        save();
    }

    /** The inbox tells how many team notices a member has not read yet (for the line after joining). */
    public void unreadCounter(ToIntFunction<UUID> counter) {
        this.unread = counter;
    }

    // ------------------------------------------------------------- sending

    /**
     * A notice of MRadmin: texts "push-&lt;text&gt;-title/-body" with the placeholders (name/value pairs).
     * @param ref  what it is about, e.g. "ticket:12", for {@link #done} and {@link #resolve} (null = nothing)
     * @param url  page of the web interface, e.g. "/admin/community/tickets/12"
     * @param who  extra filter on the receivers (null = everyone allowed), e.g. only the one who claimed a ticket
     */
    public void send(String kind, String ref, String text, String url, Predicate<UUID> who, Object... placeholders) {
        send(kind, ref, url, who, texts(text, placeholders), true);
    }

    /**
     * A notice about a problem a module reported as ticket ({@link at.marcelr.mradmin.api.MRadminService#report}): no
     * line in the game - the module tells the team there itself (e.g. MRchunks with its teleport button).
     */
    public void sendReport(String kind, String ref, String text, String url, Object... placeholders) {
        send(kind, ref, url, null, texts(text, placeholders), false);
    }

    private Map<String, String[]> texts(String text, Object... placeholders) {
        Map<String, String[]> texts = new LinkedHashMap<>();
        for (String lang : messages().languages()) {
            texts.put(lang, messages().in(lang, () -> new String[]{
                    messages().plain("push-" + text + "-title", placeholders),
                    messages().plain("push-" + text + "-body", placeholders)}));
        }
        return texts;
    }

    /** A notice with its texts per language (language -> [title, body]), e.g. from a module. Server thread. */
    public void send(String kind, String ref, String url, Predicate<UUID> who, Map<String, String[]> texts) {
        send(kind, ref, url, who, texts, true);
    }

    private void send(String kind, String ref, String url, Predicate<UUID> who, Map<String, String[]> texts, boolean line) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> send(kind, ref, url, who, texts, line));
            return;
        }
        PushService push = push();
        PushService.Kind k = push.kind(kind);
        if (k == null || push.off(kind) || texts.isEmpty()) {
            return;
        }
        Notice notice;
        synchronized (this) {
            notice = new Notice(nextId++, kind, ref, url, System.currentTimeMillis(), Map.copyOf(texts),
                    java.util.concurrent.ConcurrentHashMap.newKeySet(), false);
            notices.add(notice);
        }
        // Push in the server language, as before.
        String server = messages().serverLanguage();
        push.notify(kind, notice.title(server), notice.body(server), url, who);
        if (line && k.game() == Game.LINE) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!Npcs.is(player) && (who == null || who.test(player.getUniqueId())) && push.allowed(player, k)
                        && wantsGame(player.getUniqueId(), kind)) {
                    pending.computeIfAbsent(player.getUniqueId(), x -> new ArrayList<>())
                            .add(new Pending(notice, System.currentTimeMillis()));
                }
            }
        }
        // Who gets it in the inbox: permissions of offline members may have to be looked up - not on this thread.
        List<UUID> accounts = plugin.web().auth().accounts().stream().map(a -> a.id()).toList();
        Thread.ofVirtual().name("MRadmin-TeamNotice").start(() -> {
            for (UUID account : accounts) {
                if ((who == null || who.test(account)) && push.allowed(account, k) && push.wants(account, k, Channel.INBOX)) {
                    notice.to.add(account);
                }
            }
            save();
        });
    }

    /** Marks every notice about {@code ref} (any kind) as done, e.g. "lag" when the TPS recovered. Any thread. */
    public void resolve(String ref) {
        boolean changed = false;
        synchronized (this) {
            for (Notice n : notices) {
                if (!n.done && ref.equals(n.ref)) {
                    n.done = true;
                    changed = true;
                }
            }
        }
        if (changed) {
            save();
        }
    }

    // --------------------------------------------------------------- inbox

    /** The notices of a member that are not too old, newest first. Any thread. */
    public List<Notice> of(UUID account) {
        long limit = System.currentTimeMillis() - MAX_AGE;
        List<Notice> result = new ArrayList<>();
        synchronized (this) {
            for (int i = notices.size() - 1; i >= 0; i--) {
                Notice n = notices.get(i);
                if (n.time >= limit && n.to.contains(account)) {
                    result.add(n);
                }
            }
        }
        return result;
    }

    /** Whether the reason of the notice is gone. Server thread (looks at tickets, updates ...). */
    public boolean done(Notice n) {
        if (n.done) {
            return true;
        }
        boolean done = false;
        String ref = n.ref == null ? "" : n.ref;
        if (ref.startsWith("ticket:")) {
            Ticket t = plugin.tickets() == null ? null : plugin.tickets().get(parse(ref.substring(7)));
            // A new ticket is done once someone claimed it; everything else about it once it is closed.
            done = t == null || !t.open() || ("ticket-new".equals(n.kind) && t.claimedBy() != null);
        } else if (ref.startsWith("plugin:")) {
            String[] p = ref.split(":", 3);
            var versions = plugin.versions();
            var entry = versions == null || p.length < 3 ? null : versions.get(p[1]);
            done = entry == null || !p[2].equals(entry.staged);
        } else if (ref.equals("maintenance-soon") || ref.equals("maintenance")) {
            var phase = plugin.maintenance() == null ? null : plugin.maintenance().phase();
            done = phase == null || (ref.equals("maintenance")
                    ? phase != at.marcelr.mradmin.maintenance.MaintenanceManager.Phase.ACTIVE
                    : phase == at.marcelr.mradmin.maintenance.MaintenanceManager.Phase.NONE
                    || phase == at.marcelr.mradmin.maintenance.MaintenanceManager.Phase.ACTIVE);
        } else if (ref.startsWith("paper:")) {
            done = plugin.starter() == null || plugin.starter().paperStaged() != parse(ref.substring(6));
        }
        if (done) {
            n.done = true;
            save();
        }
        return done;
    }

    /** Alarm kinds (crash) the member has not read yet turn the letter red. */
    public boolean alarm(Notice n) {
        PushService.Kind k = push().kind(n.kind);
        return k != null && k.alarm();
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- game

    /** Whether the member wants this kind as a message in the game now (choice and not muted). */
    public boolean wantsGame(UUID player, String kind) {
        return !muted(player) && push().wants(player, kind, Channel.GAME);
    }

    /** Muted until (epoch ms; Long.MAX_VALUE = until unmuted), 0 = not muted. */
    public synchronized long mutedUntil(UUID player) {
        Long until = mutes.get(player);
        if (until != null && until <= System.currentTimeMillis()) {
            mutes.remove(player);
            return 0;
        }
        return until == null ? 0 : until;
    }

    public boolean muted(UUID player) {
        return mutedUntil(player) > 0;
    }

    /** Mutes the team messages in the game until {@code until} (Long.MAX_VALUE = until unmuted), 0 = unmute. */
    public void mute(UUID player, long until) {
        synchronized (this) {
            if (until <= 0) {
                mutes.remove(player);
            } else {
                mutes.put(player, until);
                pending.remove(player);
            }
        }
        save();
    }

    /** Every second: lines that waited long enough, grouped per kind; not while in a duel. */
    private void flushGame() {
        long now = System.currentTimeMillis();
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || muted(e.getKey())) {
                it.remove();
                continue;
            }
            List<Pending> list = e.getValue();
            long newest = list.stream().mapToLong(Pending::at).max().orElse(0);
            if (now - newest < GAME_GATHER_MILLIS || (plugin.modules() != null && plugin.modules().busy(player) != null)) {
                continue;
            }
            Map<String, List<Notice>> byKind = new LinkedHashMap<>();
            for (Pending p : list) {
                byKind.computeIfAbsent(p.notice().kind, k -> new ArrayList<>()).add(p.notice());
            }
            it.remove();
            messages().run(player, () -> byKind.values().forEach(group -> line(player, group)));
        }
    }

    /** One chat line for one notice or several of the same kind. */
    private void line(Player player, List<Notice> group) {
        String lang = messages().languageOf(player);
        Notice first = group.getLast();
        StringBuilder hover = new StringBuilder();
        for (Notice n : group.reversed()) {
            if (!hover.isEmpty()) {
                hover.append('\n');
            }
            hover.append(group.size() > 1 ? n.title(lang) + ": " : "").append(n.body(lang));
        }
        String url = group.size() > 1 ? "/inbox" : first.url;
        messages().send(player, group.size() > 1 ? "team-notice-group" : "team-notice",
                tag("title", first.title(lang)), tag("details", hover.toString()), tag("count", group.size() - 1),
                Messages.lazy("open", () -> openButton(url)));
    }

    /** "[Open]" to the page in the web interface, nothing without web.public-url. */
    private Component openButton(String url) {
        String base = plugin.settings().web().publicUrl();
        if (base.isEmpty() || !plugin.settings().web().enabled()) {
            return Component.empty();
        }
        return messages().get("team-notice-open").clickEvent(ClickEvent.openUrl(base + url));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (Npcs.is(player) || muted(id)) {
            return;
        }
        // A little later, after the other join lines (tickets, news) - and the inbox may look things up.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            int count = unread.applyAsInt(id);
            if (count > 0) {
                messages().run(player, () -> messages().send(player, "team-notice-join", tag("count", count),
                        Messages.lazy("open", () -> openButton("/inbox"))));
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    // --------------------------------------------------------- persistence

    private synchronized void load() {
        notices.clear();
        mutes.clear();
        YamlConfiguration y = plugin.storage().loadYaml(FILE, plugin.getLogger());
        nextId = Math.max(1, y.getLong("next-id", 1));
        ConfigurationSection list = y.getConfigurationSection("notices");
        long limit = System.currentTimeMillis() - MAX_AGE;
        if (list != null) {
            for (String key : list.getKeys(false)) {
                ConfigurationSection s = list.getConfigurationSection(key);
                if (s == null || s.getLong("time") < limit) {
                    continue;
                }
                try {
                    Map<String, String[]> texts = new LinkedHashMap<>();
                    ConfigurationSection t = s.getConfigurationSection("texts");
                    if (t != null) {
                        for (String lang : t.getKeys(false)) {
                            texts.put(lang, new String[]{t.getString(lang + ".title", ""), t.getString(lang + ".body", "")});
                        }
                    }
                    Set<UUID> to = java.util.concurrent.ConcurrentHashMap.newKeySet();
                    for (String u : s.getStringList("to")) {
                        to.add(UUID.fromString(u));
                    }
                    notices.add(new Notice(Long.parseLong(key), s.getString("kind", ""), s.getString("ref"),
                            s.getString("url", "/inbox"), s.getLong("time"), texts, to, s.getBoolean("done")));
                } catch (IllegalArgumentException ignored) {
                    // broken entry
                }
            }
        }
        notices.sort(java.util.Comparator.comparingLong(n -> n.id));
        ConfigurationSection m = y.getConfigurationSection("muted");
        if (m != null) {
            for (String key : m.getKeys(false)) {
                try {
                    mutes.put(UUID.fromString(key), m.getLong(key));
                } catch (IllegalArgumentException ignored) {
                    // broken entry
                }
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        synchronized (this) {
            long limit = System.currentTimeMillis() - MAX_AGE;
            notices.removeIf(n -> n.time < limit);
            while (notices.size() > MAX_NOTICES) {
                notices.removeFirst();
            }
            y.options().setHeader(List.of("MRadmin - notices for the team in the inbox of the web interface and the"
                    + " muted members (managed by the plugin, do not edit)."));
            y.set("next-id", nextId);
            for (Notice n : notices) {
                String base = "notices." + n.id + ".";
                y.set(base + "kind", n.kind);
                y.set(base + "ref", n.ref);
                y.set(base + "url", n.url);
                y.set(base + "time", n.time);
                y.set(base + "done", n.done);
                n.texts.forEach((lang, t) -> {
                    y.set(base + "texts." + lang + ".title", t[0]);
                    y.set(base + "texts." + lang + ".body", t[1]);
                });
                y.set(base + "to", new ArrayList<>(new LinkedHashSet<>(n.to.stream().map(UUID::toString).toList())));
            }
            mutes.forEach((id, until) -> y.set("muted." + id, until));
        }
        plugin.storage().saveYaml(FILE, y);
    }
}
