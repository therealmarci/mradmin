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

    /**
     * A kind of notification: who may get it (permission, feature) and whether it is on until changed. Kinds of a
     * module ({@code module} set) need its view permission - MRadmin does not name another plugin's permissions - and
     * bring their own texts ({@code info}).
     */
    public record Kind(String key, String permission, Feature feature, boolean byDefault, String module,
                       at.marcelr.mradmin.api.WebPushKind info) {

        Kind(String key, String permission, Feature feature, boolean byDefault) {
            this(key, permission, feature, byDefault, null, null);
        }
    }

    /** MRadmin's own kinds; the modules' kinds come from {@link #kinds()}. */
    public static final List<Kind> KINDS = List.of(
            new Kind("ticket-new", Perms.TICKETS_VIEW, Feature.TICKETS, true),
            new Kind("ticket-mine", Perms.TICKETS_VIEW, Feature.TICKETS, true),
            new Kind("ticket-message", Perms.TICKETS_VIEW, Feature.TICKETS, false),
            new Kind("lag", Perms.STATUS_HEALTH, Feature.SERVER_STATUS, true),
            new Kind("maintenance", Perms.MAINTENANCE_VIEW, Feature.MAINTENANCE, true));

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
                    ConfigurationSection p = a.getConfigurationSection("prefs");
                    if (p != null) {
                        Map<String, Boolean> map = new HashMap<>();
                        for (String k : p.getKeys(false)) {
                            map.put(k, p.getBoolean(k));
                        }
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
            e.getValue().forEach((k, v) -> y.set("accounts." + e.getKey() + ".prefs." + k, v));
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
        Set<String> alive = auth.sessions(account).stream().map(WebAuth.Session::id).collect(Collectors.toSet());
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
        plugin.modules().webModules().forEach((id, web) -> {
            var module = plugin.modules().module(id);
            for (var info : plugin.modules().call(module, web::pushKinds, List.<at.marcelr.mradmin.api.WebPushKind>of())) {
                if (result.stream().noneMatch(k -> k.key().equals(info.key()))) {
                    result.add(new Kind(info.key(), null, null, info.byDefault(), id, info));
                }
            }
        });
        return result;
    }

    /** The kinds this member may get, with their choice (or the default). */
    public Map<String, Boolean> choices(UUID account) {
        return choices(account, kinds());
    }

    private Map<String, Boolean> choices(UUID account, List<Kind> kinds) {
        Map<String, Boolean> own;
        synchronized (this) {
            own = Map.copyOf(prefs.getOrDefault(account, Map.of()));
        }
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Kind kind : kinds) {
            if (allowed(account, kind)) {
                result.put(kind.key(), own.getOrDefault(kind.key(), kind.byDefault()));
            }
        }
        return result;
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

    public synchronized void choose(UUID account, String kind, boolean on) {
        if (kinds().stream().anyMatch(k -> k.key().equals(kind))) {
            prefs.computeIfAbsent(account, k -> new HashMap<>()).put(kind, on);
            save();
        }
    }

    private boolean allowed(UUID account, Kind kind) {
        if (kind.feature() != null && !plugin.feature(kind.feature())) {
            return false;
        }
        if (kind.module() != null) {
            var module = plugin.modules().module(kind.module());
            return module != null && permissions.has(account, module.viewPermission());
        }
        return permissions.has(account, kind.permission());
    }

    // --------------------------------------------------------------- sending

    /**
     * Sends a notification of this kind to every member who may get it and wants it (on all their devices).
     * @param who      extra filter on the receivers (null = everyone allowed), e.g. only the one who claimed a ticket
     * @param url      page to open on a click, e.g. "/tickets/12"
     */
    public void notify(String kind, String title, String body, String url, Predicate<UUID> who) {
        if (push == null || !plugin.settings().web().enabled()) {
            return;
        }
        List<Kind> kinds = kinds();
        Kind k = kinds.stream().filter(x -> x.key().equals(kind)).findFirst().orElse(null);
        if (k == null) {
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
                if (!Boolean.TRUE.equals(choices(account, kinds).get(kind))) {
                    continue;
                }
                for (Subscription s : devices(account)) {
                    deliver(account, s, title, body, url, kind, k.key().equals("lag") || k.key().startsWith("ticket"));
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
