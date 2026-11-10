package at.marcelr.mradmin.web.push;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.web.auth.WebAuth;
import at.marcelr.mradmin.web.auth.WebPermissions;
import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Push notifications of the web interface: each member chooses per device whether to get them and per kind which
 * ones (tickets, lag, maintenance and the kinds of the modules, e.g. MRchunks' locked loaders) - only kinds they have
 * the permission for. A subscription belongs to the browser session it was made in: signing that device out (or
 * removing the account) stops its pushes. Sending happens on virtual threads, never on the server thread.
 */
public final class PushService {

    /** How a kind reaches the team in the game. */
    public enum Game {
        /** Not at all (it goes to every player anyway, e.g. maintenance). */
        NONE,
        /** With its own message of MRadmin (tickets with their buttons, the lag alarm) - which follows the choice. */
        OWN,
        /** As a short line of the team notices ({@link at.marcelr.mradmin.notify.TeamNotices}). */
        LINE
    }

    /** The three ways a team notice reaches a member. */
    public enum Channel {
        INBOX, PUSH, GAME;

        public static Channel of(String name) {
            for (Channel c : values()) {
                if (c.name().equalsIgnoreCase(name)) {
                    return c;
                }
            }
            return PUSH;
        }
    }

    /**
     * A kind of notification: who may get it (permission, feature) and whether its push is on until changed. Kinds of
     * a module ({@code module} set) need its view permission - MRadmin does not name another plugin's permissions -
     * and bring their own texts ({@code info}). {@code alarm}: always in the inbox (only push and game can be switched
     * off) and turns the letter red.
     */
    public record Kind(String key, String permission, Feature feature, boolean byDefault, String module,
                       at.marcelr.mradmin.api.WebPushKind info, Game game, boolean alarm) {

        Kind(String key, String permission, Feature feature, boolean byDefault, Game game) {
            this(key, permission, feature, byDefault, null, null, game, false);
        }
    }

    /** MRadmin's own kinds; the modules' kinds come from {@link #kinds()}. */
    public static final List<Kind> KINDS = List.of(
            new Kind("ticket-new", Perms.TICKETS_VIEW, Feature.TICKETS, true, Game.OWN),
            new Kind("ticket-mine", Perms.TICKETS_VIEW, Feature.TICKETS, true, Game.OWN),
            new Kind("ticket-message", Perms.TICKETS_VIEW, Feature.TICKETS, false, Game.OWN),
            new Kind("application", Perms.APPLICATIONS, Feature.TICKETS, true, Game.LINE),
            new Kind("server-crash", Perms.SERVER_RESTART, null, true, null, null, Game.LINE, true),
            new Kind("update-available", Perms.VERSIONS_VIEW, null, false, Game.OWN),
            new Kind("update-ready", Perms.VERSIONS_VIEW, null, false, Game.LINE),
            new Kind("lag", Perms.STATUS_HEALTH, Feature.SERVER_STATUS, true, Game.OWN),
            new Kind("maintenance", Perms.MAINTENANCE_VIEW, Feature.MAINTENANCE, true, Game.NONE));

    /** A browser that receives pushes. */
    public record Subscription(String endpoint, String p256dh, String auth, String session, String device, long created) {
    }

    private static final String STATE = "web-push";
    private static final int TTL_SECONDS = 24 * 3600;
    private static final int MAX_DEVICES = 10;

    private final MRAdmin plugin;
    private final WebAuth auth;
    private final WebPermissions permissions;
    private final Map<UUID, List<Subscription>> subscriptions = new HashMap<>();
    private final Map<UUID, Map<String, Boolean>> prefs = new HashMap<>();
    private WebPush push;
    private final Map<String, Long> lastFailureLog = new HashMap<>();
    private volatile String warnedContact;

    public PushService(MRAdmin plugin, WebAuth auth, WebPermissions permissions) {
        this.plugin = plugin;
        this.auth = auth;
        this.permissions = permissions;
    }

    // ----------------------------------------------------------- persistence

    /** Loads keys, devices and choices; makes the server's key pair on first start. */
    public synchronized void load() {
        YamlConfiguration y = plugin.storage().loadYaml(STATE, plugin.getLogger());
        try {
            String priv = y.getString("vapid.private");
            String pub = y.getString("vapid.public");
            KeyPair pair;
            if (priv == null || pub == null) {
                pair = WebPush.newKeyPair();
                y.set("vapid.private", WebPush.privateText(pair));
                y.set("vapid.public", WebPush.publicText(pair));
            } else {
                pair = WebPush.load(priv, pub);
            }
            push = new WebPush(pair, this::contact);
            contact();
        } catch (GeneralSecurityException e) {
            plugin.getLogger().warning("Push notifications are off - no key: " + e.getMessage());
            push = null;
        }
        subscriptions.clear();
        prefs.clear();
        ConfigurationSection accounts = y.getConfigurationSection("accounts");
        if (accounts != null) {
            for (String key : accounts.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ConfigurationSection a = accounts.getConfigurationSection(key);
                    List<Subscription> list = new ArrayList<>();
                    ConfigurationSection devices = a.getConfigurationSection("devices");
                    if (devices != null) {
                        for (String d : devices.getKeys(false)) {
                            ConfigurationSection s = devices.getConfigurationSection(d);
                            list.add(new Subscription(s.getString("endpoint"), s.getString("p256dh"), s.getString("auth"),
                                    s.getString("session", ""), s.getString("device", ""), s.getLong("created")));
                        }
                    }
                    subscriptions.put(id, list);
                    // prefs = push (as before), prefs-inbox / prefs-game = the other channels
                    Map<String, Boolean> map = new HashMap<>();
                    for (Channel c : Channel.values()) {
                        ConfigurationSection p = a.getConfigurationSection(section(c));
                        if (p != null) {
                            for (String k : p.getKeys(false)) {
                                map.put(prefKey(c, k), p.getBoolean(k));
                            }
                        }
                    }
                    if (!map.isEmpty()) {
                        prefs.put(id, map);
                    }
                } catch (RuntimeException ignored) {
                    // a broken entry - that device subscribes again
                }
            }
        }
        save(y);
    }

    /** web.push.contact, else the public https address; warns when Apple devices would refuse it. */
    private String contact() {
        var web = plugin.settings().web();
        String contact = web.pushContact().isEmpty() && web.publicUrl().startsWith("https://")
                ? web.publicUrl() : web.pushContact();
        if (WebPush.appleContact(contact)) {
            return contact;
        }
        // Asked at every push: warn once per value (again after a reload changed it).
        if (web.enabled() && !contact.equals(warnedContact)) {
            warnedContact = contact;
            plugin.getLogger().warning("Push notifications will not reach iPhones, iPads and Safari: set web.push.contact"
                    + " to \"mailto:you@yourdomain.com\" or a public https:// address (now: "
                    + (contact.isEmpty() ? "nothing" : "\"" + contact + "\"") + ").");
        }
        // Google and Mozilla take this, Apple does not.
        return contact.startsWith("mailto:") || contact.startsWith("https://") ? contact : "mailto:mradmin@localhost";
    }

    private synchronized void save() {
        save(plugin.storage().loadYaml(STATE, plugin.getLogger()));
    }

    private static String section(Channel c) {
        return c == Channel.PUSH ? "prefs" : "prefs-" + c.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Key of a choice in memory: the kind for push (as before), "inbox:kind" / "game:kind" for the others. */
    private static String prefKey(Channel c, String kind) {
        return c == Channel.PUSH ? kind : c.name().toLowerCase(java.util.Locale.ROOT) + ":" + kind;
    }

    private void save(YamlConfiguration y) {
        y.set("accounts", null);
        for (Map.Entry<UUID, List<Subscription>> e : subscriptions.entrySet()) {
            String base = "accounts." + e.getKey() + ".";
            for (int i = 0; i < e.getValue().size(); i++) {
                Subscription s = e.getValue().get(i);
                String p = base + "devices." + i + ".";
                y.set(p + "endpoint", s.endpoint());
                y.set(p + "p256dh", s.p256dh());
                y.set(p + "auth", s.auth());
                y.set(p + "session", s.session());
                y.set(p + "device", s.device());
                y.set(p + "created", s.created());
            }
        }
        for (Map.Entry<UUID, Map<String, Boolean>> e : prefs.entrySet()) {
            e.getValue().forEach((k, v) -> {
                int colon = k.indexOf(':');
                Channel c = colon < 0 ? Channel.PUSH : Channel.of(k.substring(0, colon));
                y.set("accounts." + e.getKey() + "." + section(c) + "." + k.substring(colon + 1), v);
            });
        }
        y.options().setHeader(List.of("MRadmin - push notifications (managed by the plugin, do not edit)."));
        plugin.storage().saveYaml(STATE, y);
    }

    // --------------------------------------------------------------- members

    public boolean available() {
        return push != null;
    }

    public String publicKey() {
        return push == null ? null : push.publicKey();
    }

    /** Adds (or renews) this browser for the account, bound to the session it was made in. */
    public synchronized String subscribe(UUID account, String session, String endpoint, String p256dh, String auth,
                                         String device) {
        if (!endpoint.startsWith("https://") || endpoint.length() > 1000 || p256dh == null || auth == null) {
            return "push-invalid";
        }
        List<Subscription> list = subscriptions.computeIfAbsent(account, k -> new ArrayList<>());
        list.removeIf(s -> s.endpoint().equals(endpoint) || s.session().equals(session));
        if (list.size() >= MAX_DEVICES) {
            list.removeFirst();
        }
        list.add(new Subscription(endpoint, p256dh, auth, session, device, System.currentTimeMillis()));
        save();
        return null;
    }

    public synchronized void unsubscribe(UUID account, String endpoint) {
        List<Subscription> list = subscriptions.get(account);
        if (list != null && list.removeIf(s -> s.endpoint().equals(endpoint))) {
            save();
        }
    }

    /** The account's devices that still have a valid session (the others are removed). */
    public synchronized List<Subscription> devices(UUID account) {
        List<Subscription> list = subscriptions.getOrDefault(account, List.of());
        Set<String> alive = auth.sessions(account).stream().map(at.marcelr.mradmin.web.self.PlayerSessions.Session::id).collect(Collectors.toSet());
        List<Subscription> valid = list.stream().filter(s -> alive.contains(s.session())).toList();
        if (valid.size() != list.size()) {
            subscriptions.put(account, new ArrayList<>(valid));
            save();
        }
        return valid;
    }

    /** MRadmin's kinds and those of the connected modules ({@link at.marcelr.mradmin.api.WebModule#pushKinds}). */
    public List<Kind> kinds() {
        List<Kind> result = new ArrayList<>(KINDS);
        if (plugin.modules() == null) {
            return result;
        }
        plugin.modules().webModules().forEach((id, web) -> {
            var module = plugin.modules().module(id);
            for (var info : plugin.modules().call(module, web::pushKinds, List.<at.marcelr.mradmin.api.WebPushKind>of())) {
                if (result.stream().noneMatch(k -> k.key().equals(info.key()))) {
                    result.add(new Kind(info.key(), null, null, info.byDefault(), id, info, Game.OWN, false));
                }
            }
        });
        return result;
    }

    /** The kind with this key, or null. */
    public Kind kind(String key) {
        return kinds().stream().filter(k -> k.key().equals(key)).findFirst().orElse(null);
    }

    /** Switched off for everyone in config.yml (notifications.disabled): no inbox, push or game line. */
    public boolean off(String kind) {
        return plugin.getConfig().getStringList("notifications.disabled").contains(kind);
    }

    /**
     * The kinds this member may get, each with the choice per channel (or the default): push, inbox and - unless the
     * kind never reaches the game - game; {@code alarm} kinds are always in the inbox.
     */
    public Map<String, Map<String, Object>> choices(UUID account) {
        Map<String, Boolean> own;
        synchronized (this) {
            own = Map.copyOf(prefs.getOrDefault(account, Map.of()));
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Kind kind : kinds()) {
            if (!off(kind.key()) && allowed(account, kind)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("inbox", kind.alarm() || own.getOrDefault(prefKey(Channel.INBOX, kind.key()), true));
                c.put("push", own.getOrDefault(kind.key(), kind.byDefault()));
                if (kind.game() != Game.NONE) {
                    c.put("game", own.getOrDefault(prefKey(Channel.GAME, kind.key()), true));
                }
                if (kind.alarm()) {
                    c.put("alarm", true);
                }
                // For grouping by plugin in the account page.
                var module = kind.module() == null ? null : plugin.modules().module(kind.module());
                c.put("plugin", module == null ? plugin.getName() : module.plugin().getName());
                result.put(kind.key(), c);
            }
        }
        return result;
    }

    /** Whether the member wants this kind on this channel (permission not checked here). */
    public boolean wants(UUID account, Kind kind, Channel channel) {
        if (off(kind.key())) {
            return false;
        }
        if (channel == Channel.INBOX && kind.alarm()) {
            return true;
        }
        if (channel == Channel.GAME && kind.game() == Game.NONE) {
            return false;
        }
        Boolean own;
        synchronized (this) {
            own = prefs.getOrDefault(account, Map.of()).get(prefKey(channel, kind.key()));
        }
        return own != null ? own : channel != Channel.PUSH || kind.byDefault();
    }

    /** Like {@link #wants(UUID, Kind, Channel)} by the kind's key; unknown kinds: yes. */
    public boolean wants(UUID account, String kind, Channel channel) {
        Kind k = kind(kind);
        return k == null || wants(account, k, channel);
    }

    /** Texts of the modules' kinds this member may get: key -> label, hint, icon (MRadmin's own are in the web UI). */
    public Map<String, Map<String, String>> labels(UUID account) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Kind kind : kinds()) {
            if (kind.info() != null && allowed(account, kind)) {
                result.put(kind.key(), Map.of("label", kind.info().label(), "hint", kind.info().hint(),
                        "icon", kind.info().icon()));
            }
        }
        return result;
    }

    public synchronized void choose(UUID account, String kind, Channel channel, boolean on) {
        Kind k = kind(kind);
        if (k != null && !(channel == Channel.INBOX && k.alarm()) && !(channel == Channel.GAME && k.game() == Game.NONE)) {
            prefs.computeIfAbsent(account, x -> new HashMap<>()).put(prefKey(channel, kind), on);
            save();
        }
    }

    /** Whether this member may get the kind (feature on, permission - also for offline members). */
    public boolean allowed(UUID account, Kind kind) {
        if (kind.feature() != null && !plugin.feature(kind.feature())) {
            return false;
        }
        if (kind.module() != null) {
            var module = plugin.modules().module(kind.module());
            return module != null && permissions.has(account, module.viewPermission());
        }
        return permissions.has(account, kind.permission());
    }

    /** Like {@link #allowed(UUID, Kind)} for a player online (their live permissions). */
    public boolean allowed(org.bukkit.entity.Player player, Kind kind) {
        if (kind.feature() != null && !plugin.feature(kind.feature())) {
            return false;
        }
        if (kind.module() != null) {
            var module = plugin.modules().module(kind.module());
            return module != null && player.hasPermission(module.viewPermission());
        }
        return player.hasPermission(kind.permission());
    }

    // --------------------------------------------------------------- sending

    /**
     * Sends a notification of this kind to every member who may get it and wants it (on all their devices).
     * @param who      extra filter on the receivers (null = everyone allowed), e.g. only the one who claimed a ticket
     * @param url      page to open on a click, e.g. "/admin/community/tickets/12"
     */
    public void notify(String kind, String title, String body, String url, Predicate<UUID> who) {
        if (push == null || !plugin.settings().web().enabled()) {
            return;
        }
        Kind k = kind(kind);
        if (k == null || off(kind)) {
            return;
        }
        Map<UUID, List<Subscription>> copy;
        synchronized (this) {
            copy = new HashMap<>(subscriptions);
        }
        Thread.ofVirtual().name("MRadmin-Push").start(() -> {
            for (Map.Entry<UUID, List<Subscription>> e : copy.entrySet()) {
                UUID account = e.getKey();
                if (who != null && !who.test(account)) {
                    continue;
                }
                if (!allowed(account, k) || !wants(account, k, Channel.PUSH)) {
                    continue;
                }
                for (Subscription s : devices(account)) {
                    deliver(account, s, title, body, url, kind, k.alarm() || k.key().equals("lag") || k.key().startsWith("ticket"));
                }
            }
        });
    }

    /** What a test push to one device gave: the device name and the push service's answer. */
    public record TestResult(String device, WebPush.Result result) {
    }

    /** A test notification to the member's own devices; waits for the push services (call off the main thread). */
    public List<TestResult> test(UUID account, String title, String body) {
        List<Subscription> list = devices(account);
        List<CompletableFuture<WebPush.Result>> sent = new ArrayList<>();
        for (Subscription s : list) {
            sent.add(deliver(account, s, title, body, "/account", "test", true));
        }
        List<TestResult> results = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            WebPush.Result result;
            try {
                result = sent.get(i).get(20, TimeUnit.SECONDS);
            } catch (Exception e) {
                result = new WebPush.Result(WebPush.Outcome.FAILED, 0, "timeout");
            }
            results.add(new TestResult(list.get(i).device(), result));
        }
        return results;
    }

    private CompletableFuture<WebPush.Result> deliver(UUID account, Subscription s, String title, String body,
                                                      String url, String tag, boolean urgent) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", title);
        payload.addProperty("body", body.length() > 300 ? body.substring(0, 300) + "…" : body);
        payload.addProperty("url", url);
        payload.addProperty("tag", tag);
        return push.send(s.endpoint(), s.p256dh(), s.auth(), payload.toString(), TTL_SECONDS, urgent).thenApply(result -> {
            if (result.outcome() == WebPush.Outcome.GONE) {
                // The browser revoked the permission or the subscription expired.
                unsubscribe(account, s.endpoint());
            } else if (result.outcome() == WebPush.Outcome.FAILED) {
                logFailure(s, result);
            }
            return result;
        });
    }

    /** Push services that refuse a push say why - logged at most every 10 minutes per service. */
    private void logFailure(Subscription s, WebPush.Result result) {
        String host;
        try {
            host = URI.create(s.endpoint()).getHost();
        } catch (IllegalArgumentException e) {
            host = "?";
        }
        long now = System.currentTimeMillis();
        synchronized (lastFailureLog) {
            if (now - lastFailureLog.getOrDefault(host, 0L) < 10 * 60_000L) {
                return;
            }
            lastFailureLog.put(host, now);
        }
        plugin.getLogger().warning("Push to " + s.device() + " refused by " + host + ": "
                + (result.status() == 0 ? "" : "HTTP " + result.status() + " ")
                + (result.reason().isEmpty() ? "(no reason)" : result.reason())
                + (result.reason().contains("BadJwtToken") ? " - set web.push.contact (see config.yml)." : ""));
    }

    /** When a web account is deleted. */
    public synchronized void forget(UUID account) {
        if (subscriptions.remove(account) != null | prefs.remove(account) != null) {
            save();
        }
    }
}
