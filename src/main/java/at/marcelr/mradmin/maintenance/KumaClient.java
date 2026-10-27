package at.marcelr.mradmin.maintenance;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.json.JSONObject;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Connection to Uptime Kuma over Socket.IO - the same interface its web UI uses. After the login Kuma pushes the
 * full maintenance list ({@code maintenanceList}) and pushes it again whenever a maintenance is added, edited,
 * paused, resumed or deleted. In addition the list is requested every {@code refresh-seconds}, so the next slot of
 * recurring maintenances is picked up.
 * <p>
 * All Socket.IO callbacks run on the client's own thread; results are handed to the main thread.
 */
public final class KumaClient {

    public enum State { DISABLED, CONNECTING, CONNECTED, LOGIN_FAILED, OFFLINE }

    private final MRAdmin plugin;
    private final Consumer<List<JsonObject>> onList;
    private final Consumer<Boolean> onConnectionChange;

    private volatile Socket socket;
    private volatile State state = State.DISABLED;
    /** A maintenance list has arrived since the last login (see {@link #upToDate()}). */
    private volatile boolean listSinceLogin;
    /** Language key or raw text of the last problem (shown in /mradmin maintenance). */
    private volatile String problemKey;
    private volatile String problemDetail;
    private volatile Instant lastUpdate;
    private volatile int lastCount;
    /** JWT from the last password login, used for reconnects so 2FA is not asked again. */
    private volatile String sessionToken;
    private BukkitTask refreshTask;
    private Settings.KumaSettings settings;
    private String loggedProblem;

    /**
     * @param onList             receives the (filtered) raw maintenance objects, on the main thread
     * @param onConnectionChange true when logged in, false when the connection is lost (main thread)
     */
    public KumaClient(MRAdmin plugin, Consumer<List<JsonObject>> onList, Consumer<Boolean> onConnectionChange) {
        this.plugin = plugin;
        this.onList = onList;
        this.onConnectionChange = onConnectionChange;
    }

    public void start(Settings.KumaSettings settings) {
        stop();
        this.settings = settings;
        if (!settings.enabled()) {
            state = State.DISABLED;
            problemKey = "kuma-reason-disabled";
            return;
        }
        if (settings.url().isEmpty()) {
            problem("kuma-reason-no-url", null, State.OFFLINE);
            return;
        }
        if (!settings.hasLogin()) {
            problem("kuma-reason-no-login", null, State.OFFLINE);
            return;
        }
        URI uri;
        try {
            uri = URI.create(settings.url());
            if (uri.getHost() == null || !(Objects.equals(uri.getScheme(), "http") || Objects.equals(uri.getScheme(), "https"))) {
                throw new IllegalArgumentException("no http(s) address");
            }
        } catch (IllegalArgumentException e) {
            problem("kuma-reason-bad-url", null, State.OFFLINE);
            return;
        }
        // Kuma behind a reverse proxy with a sub path (https://example.com/kuma) -> /kuma/socket.io/
        String basePath = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        URI origin = URI.create(uri.getScheme() + "://" + uri.getRawAuthority());

        IO.Options options = IO.Options.builder()
                .setPath(basePath + "/socket.io/")
                .setReconnection(true)
                .setReconnectionDelay(settings.reconnectSeconds() * 1000L)
                .setReconnectionDelayMax(settings.reconnectSeconds() * 1000L)
                .setTimeout(15_000)
                .setExtraHeaders(Map.of("User-Agent", List.of("MRadmin/" + plugin.getPluginMeta().getVersion())))
                .build();
        Socket created;
        try {
            created = IO.socket(origin, options);
        } catch (RuntimeException e) {
            problem(null, e.getMessage(), State.OFFLINE);
            return;
        }
        socket = created;
        state = State.CONNECTING;
        created.on(Socket.EVENT_CONNECT, args -> login(created));
        created.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Object error = args.length > 0 ? args[0] : null;
            String text = error instanceof Throwable t ? t.getMessage() : String.valueOf(error);
            problem("kuma-reason-unreachable", text, State.OFFLINE);
        });
        created.on(Socket.EVENT_DISCONNECT, args -> {
            boolean wasConnected = state == State.CONNECTED;
            if (state != State.LOGIN_FAILED) {
                problem("kuma-reason-disconnected", args.length > 0 ? String.valueOf(args[0]) : null, State.OFFLINE);
            }
            if (wasConnected) {
                Bukkit.getScheduler().runTask(plugin, () -> onConnectionChange.accept(false));
            }
        });
        // Kuma with disabled authentication logs every client in by itself.
        created.on("autoLogin", args -> loggedIn(created));
        // After the login Kuma tells its version (shown in the web interface).
        created.on("info", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject info && info.has("version")) {
                version = info.optString("version", null);
            }
        });
        created.on("maintenanceList", args -> {
            if (args.length > 0 && args[0] instanceof JSONObject list) {
                receive(created, list);
            }
        });
        created.connect();
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh,
                settings.refreshSeconds() * 20L, settings.refreshSeconds() * 20L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        Socket current = socket;
        socket = null;
        if (current != null) {
            current.off();
            current.close();
        }
        state = State.DISABLED;
    }

    /** Asks Kuma to send the maintenance list again (it answers with a {@code maintenanceList} push). */
    public void refresh() {
        Socket current = socket;
        if (current != null && state == State.CONNECTED) {
            current.emit("getMaintenanceList", (Ack) args -> { });
        }
    }

    // ------------------------------------------------------------------ login

    private void login(Socket current) {
        String token = !settings.token().isEmpty() ? settings.token() : sessionToken;
        if (token != null) {
            current.emit("loginByToken", token, (Ack) args -> {
                JSONObject result = result(args);
                if (result.optBoolean("ok")) {
                    loggedIn(current);
                } else if (settings.token().isEmpty()) {
                    // The stored session expired (password changed?) -> log in with the password again.
                    sessionToken = null;
                    passwordLogin(current);
                } else {
                    problem("kuma-reason-bad-token", result.optString("msg", null), State.LOGIN_FAILED);
                }
            });
            return;
        }
        passwordLogin(current);
    }

    private void passwordLogin(Socket current) {
        JSONObject data = new JSONObject();
        try {
            data.put("username", settings.username());
            data.put("password", settings.password());
            data.put("token", "");
        } catch (org.json.JSONException e) {
            problem(null, e.getMessage(), State.LOGIN_FAILED);
            return;
        }
        current.emit("login", data, (Ack) args -> {
            JSONObject result = result(args);
            if (result.optBoolean("ok")) {
                String jwt = result.optString("token", null);
                if (jwt != null && !jwt.isEmpty()) {
                    sessionToken = jwt;
                }
                loggedIn(current);
            } else if (result.optBoolean("tokenRequired")) {
                problem("kuma-reason-2fa", null, State.LOGIN_FAILED);
            } else {
                problem("kuma-reason-login-failed", result.optString("msg", null), State.LOGIN_FAILED);
            }
        });
    }

    private void loggedIn(Socket current) {
        if (current != socket) {
            return;
        }
        boolean changed = state != State.CONNECTED;
        if (changed) {
            listSinceLogin = false;
        }
        state = State.CONNECTED;
        problemKey = null;
        problemDetail = null;
        loggedProblem = null;
        if (changed) {
            plugin.getLogger().info("Connected to Uptime Kuma at " + settings.url() + ".");
            Bukkit.getScheduler().runTask(plugin, () -> onConnectionChange.accept(true));
        }
        // Kuma sends the list right after the login anyway; ask once more in case it was missed.
        current.emit("getMaintenanceList", (Ack) args -> { });
    }

    private static JSONObject result(Object[] args) {
        return args.length > 0 && args[0] instanceof JSONObject json ? json : new JSONObject();
    }

    // ---------------------------------------------------------------- receive

    private void receive(Socket current, JSONObject raw) {
        JsonElement parsed = JsonParser.parseString(raw.toString());
        if (!parsed.isJsonObject()) {
            return;
        }
        List<JsonObject> all = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonObject()) {
                all.add(entry.getValue().getAsJsonObject());
            }
        }
        Set<Integer> wanted = settings.maintenanceIds();
        Set<Integer> monitors = settings.monitorIds();
        List<JsonObject> byId = all.stream()
                .filter(m -> m.has("id") && (wanted.isEmpty() && monitors.isEmpty() || wanted.contains(m.get("id").getAsInt())))
                .toList();
        if (monitors.isEmpty()) {
            deliver(byId);
            return;
        }
        // Monitor filter: ask Kuma which monitors each remaining maintenance affects.
        List<JsonObject> candidates = all.stream().filter(m -> m.has("id") && !byId.contains(m)).toList();
        List<CompletableFuture<JsonObject>> checks = new ArrayList<>();
        for (JsonObject m : candidates) {
            CompletableFuture<JsonObject> check = new CompletableFuture<>();
            checks.add(check.completeOnTimeout(null, 10, TimeUnit.SECONDS));
            current.emit("getMonitorMaintenance", m.get("id").getAsInt(), (Ack) args -> {
                JSONObject result = result(args);
                var list = result.optJSONArray("monitors");
                boolean affected = false;
                for (int i = 0; list != null && i < list.length(); i++) {
                    JSONObject monitor = list.optJSONObject(i);
                    if (monitor != null && monitors.contains(monitor.optInt("id", -1))) {
                        affected = true;
                        break;
                    }
                }
                check.complete(affected ? m : null);
            });
        }
        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenRun(() -> {
            List<JsonObject> result = new ArrayList<>(byId);
            checks.stream().map(CompletableFuture::join).filter(Objects::nonNull).forEach(result::add);
            deliver(result);
        });
    }

    private void deliver(List<JsonObject> maintenances) {
        lastUpdate = Instant.now();
        lastCount = maintenances.size();
        lastList = List.copyOf(maintenances);
        Bukkit.getScheduler().runTask(plugin, () -> {
            onList.accept(maintenances);
            listSinceLogin = true;
        });
    }

    /**
     * Whether the maintenance list is current: logged in and a list has arrived since. Right after a (re)connect
     * Kuma counts as connected before its list is there - in between, nothing may be taken as "no maintenance".
     */
    public boolean upToDate() {
        return state == State.CONNECTED && listSinceLogin;
    }

    // ---------------------------------------------------------------- manage

    private static final long CALL_SECONDS = 15;
    private static final java.time.format.DateTimeFormatter KUMA_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** The maintenances for this server as Kuma sent them (filtered like the schedule), for the web interface. */
    private volatile List<JsonObject> lastList = List.of();

    public List<JsonObject> maintenances() {
        return lastList;
    }

    /** Kuma's version (after the login), or null. */
    public String version() {
        return version;
    }

    private volatile String version;

    /**
     * Whether new maintenances made here show up for this server: with maintenance-ids only, a new one would not be
     * in the list - then only the listed ones can be managed.
     */
    public boolean canCreate() {
        return settings != null && (settings.maintenanceIds().isEmpty() || !settings.monitorIds().isEmpty());
    }

    /** Whether this maintenance is one of this server's (only those may be changed from here). */
    public boolean owns(int id) {
        return lastList.stream().anyMatch(m -> m.has("id") && m.get("id").getAsInt() == id);
    }

    /**
     * New maintenance in Kuma: {@code start}/{@code end} null = "manual" (active until it is paused or deleted),
     * otherwise a single window. Times go in MRadmin's timezone. It is attached to uptime-kuma.monitor-ids, so it
     * counts for this server (and shows on those monitors' status pages). Blocking - web thread only.
     * @return null or an error text
     */
    public String add(String title, String description, Instant start, Instant end, java.time.ZoneId zone) {
        JSONObject m = new JSONObject();
        try {
            boolean manual = start == null;
            m.put("title", title);
            m.put("description", description == null ? "" : description);
            m.put("strategy", manual ? "manual" : "single");
            m.put("active", true);
            m.put("intervalDay", 1);
            org.json.JSONArray range = new org.json.JSONArray();
            if (!manual) {
                range.put(KUMA_TIME.format(start.atZone(zone)));
                range.put(KUMA_TIME.format(end.atZone(zone)));
            }
            m.put("dateRange", range);
            // Only used by recurring maintenances, but Kuma expects the fields.
            org.json.JSONArray times = new org.json.JSONArray();
            times.put(new JSONObject().put("hours", 2).put("minutes", 0));
            times.put(new JSONObject().put("hours", 3).put("minutes", 0));
            m.put("timeRange", times);
            m.put("weekdays", new org.json.JSONArray());
            m.put("daysOfMonth", new org.json.JSONArray());
            m.put("cron", "30 3 * * *");
            m.put("durationMinutes", 60);
            m.put("timezoneOption", zone.getId());
        } catch (org.json.JSONException e) {
            return e.getMessage();
        }
        JSONObject result = call("addMaintenance", m);
        if (!result.optBoolean("ok")) {
            return result.optString("msg", "failed");
        }
        int id = result.optInt("maintenanceID", -1);
        if (id > 0 && !settings.monitorIds().isEmpty()) {
            org.json.JSONArray monitors = new org.json.JSONArray();
            try {
                for (int monitor : settings.monitorIds()) {
                    monitors.put(new JSONObject().put("id", monitor));
                }
            } catch (org.json.JSONException e) {
                return e.getMessage();
            }
            JSONObject linked = call("addMonitorMaintenance", id, monitors);
            if (!linked.optBoolean("ok")) {
                return linked.optString("msg", "failed");
            }
        }
        refresh();
        return null;
    }

    /**
     * Changes title/description and - for a single window - start and end (null = unchanged). Blocking.
     * @return null or an error text
     */
    public String edit(int id, String title, String description, Instant start, Instant end, java.time.ZoneId zone) {
        JSONObject current = call("getMaintenance", id);
        JSONObject m = current.optJSONObject("maintenance");
        if (!current.optBoolean("ok") || m == null) {
            return current.optString("msg", "not found");
        }
        try {
            if (title != null) {
                m.put("title", title);
            }
            if (description != null) {
                m.put("description", description);
            }
            if (start != null || end != null) {
                if (!"single".equals(m.optString("strategy"))) {
                    return "only single maintenances can be moved";
                }
                java.time.ZoneId own = KumaParser.zone(JsonParser.parseString(m.toString()).getAsJsonObject(), zone);
                org.json.JSONArray range = m.optJSONArray("dateRange");
                Instant oldStart = range == null ? null : KumaParser.time(range.optString(0, null), own);
                Instant oldEnd = range == null ? null : KumaParser.time(range.optString(1, null), own);
                Instant newStart = start != null ? start : oldStart;
                Instant newEnd = end != null ? end : oldEnd;
                if (newStart == null || newEnd == null || !newEnd.isAfter(newStart)) {
                    return "end before start";
                }
                org.json.JSONArray updated = new org.json.JSONArray();
                updated.put(KUMA_TIME.format(newStart.atZone(zone)));
                updated.put(KUMA_TIME.format(newEnd.atZone(zone)));
                m.put("dateRange", updated);
                m.put("timezoneOption", zone.getId());
            }
        } catch (org.json.JSONException e) {
            return e.getMessage();
        }
        JSONObject result = call("editMaintenance", m);
        if (!result.optBoolean("ok")) {
            return result.optString("msg", "failed");
        }
        refresh();
        return null;
    }

    /** pause | resume | delete. Blocking. @return null or an error text */
    public String simple(String event, int id) {
        JSONObject result = call(event, id);
        if (!result.optBoolean("ok")) {
            return result.optString("msg", "failed");
        }
        refresh();
        return null;
    }

    /** Sends an event and waits for Kuma's answer (empty object without connection or after the timeout). */
    private JSONObject call(String event, Object... args) {
        Socket current = socket;
        if (current == null || state != State.CONNECTED) {
            JSONObject offline = new JSONObject();
            try {
                offline.put("ok", false).put("msg", "Uptime Kuma is not connected");
            } catch (org.json.JSONException ignored) {
                // cannot happen
            }
            return offline;
        }
        CompletableFuture<JSONObject> answer = new CompletableFuture<>();
        Object[] withAck = java.util.Arrays.copyOf(args, args.length + 1);
        withAck[args.length] = (Ack) reply -> answer.complete(result(reply));
        current.emit(event, withAck);
        try {
            return answer.get(CALL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            JSONObject failed = new JSONObject();
            try {
                failed.put("ok", false).put("msg", "no answer from Uptime Kuma");
            } catch (org.json.JSONException ignored) {
                // cannot happen
            }
            return failed;
        }
    }

    // ---------------------------------------------------------------- status

    private void problem(String key, String detail, State newState) {
        state = newState;
        problemKey = key;
        problemDetail = detail;
        String text = (key == null ? "" : plugin.messages().console(key)) + (detail == null || detail.isBlank() ? "" : " (" + detail + ")");
        // Log each problem only once, a server that is down would otherwise spam the console on every retry.
        if (!text.equals(loggedProblem)) {
            loggedProblem = text;
            plugin.getLogger().warning("Uptime Kuma: " + text.trim());
        }
    }

    public State state() {
        return state;
    }

    /** The current problem in the reader's language, or null. */
    public String problem() {
        String key = problemKey;
        String detail = problemDetail;
        if (key == null && detail == null) {
            return null;
        }
        String text = key == null ? "" : plugin.messages().plain(key);
        return (text + (detail == null || detail.isBlank() ? "" : " (" + detail + ")")).trim();
    }

    public Instant lastUpdate() {
        return lastUpdate;
    }

    public int lastCount() {
        return lastCount;
    }
}
