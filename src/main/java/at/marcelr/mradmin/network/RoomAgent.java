package at.marcelr.mradmin.network;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.health.HealthMonitor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Reads the waiting room server through its agent (MRwaitroom, section agent) - like MRadmin reads its own server:
 * every second the new health points, which go into a second {@link HealthMonitor} (same recording, same charts, same
 * lag alarm). System info, lag chunks, console and the older server log are only asked when someone looks (passed
 * through, {@link #get} / {@link #post}). Every 3 seconds its events go into MRadmin's own log (source "waitroom").
 * <p>
 * The address comes from {@link WaitingRoom#agentUrl()}; the key is the one of the waiting room link. Runs in a virtual
 * thread, never on the server thread. Without a waiting room (or with the agent switched off there) it just waits.
 */
public final class RoomAgent {

    private static final Gson GSON = new Gson();
    private static final long POLL_MILLIS = 1000;

    private final MRAdmin plugin;
    private final HealthMonitor health;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3)).build();
    private volatile Thread thread;
    /** When the agent last answered (epoch ms), 0 = not since this start. */
    private volatile long lastAnswer;
    private volatile String lastProblem = "";
    /** Time of the newest event of the waiting room already in the log (0 = look it up in the log first). */
    private long lastEvent = -1;
    private int round;
    /** The newest control state of the waiting room (countdown, pause, starter, Paper ...) and its 1-minute TPS. */
    private volatile java.util.Map<String, Object> control = java.util.Map.of();
    private volatile double tps1m = 20;
    /** The players on the waiting room server (uuid, name, world, ping) and its slots - for the network dashboard. */
    private volatile List<java.util.Map<String, Object>> players = List.of();
    private volatile int maxPlayers;
    /** Source of the waiting room's events in MRadmin's log. */
    public static final String SOURCE = "waitroom";

    public RoomAgent(MRAdmin plugin) {
        this.plugin = plugin;
        this.health = new HealthMonitor(plugin, "waitroom-health");
    }

    /** The recorded health of the waiting room server. */
    public HealthMonitor health() {
        return health;
    }

    /** Countdown, pause and server values of the waiting room as of its last answer (empty while it does not answer). */
    public java.util.Map<String, Object> control() {
        return online() ? control : java.util.Map.of();
    }

    /** The waiting room's 1-minute TPS as of its last answer. */
    /** The players on the waiting room server right now (empty while it does not answer). */
    public List<java.util.Map<String, Object>> players() {
        return online() ? players : List.of();
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public double tps() {
        return tps1m;
    }

    /** Whether the agent answered within the last few seconds. */
    public boolean online() {
        return System.currentTimeMillis() - lastAnswer < 5000;
    }

    /** On enable and after every reload: loads the stored history and starts asking. */
    public void start() {
        stop();
        health.start();
        Thread t = Thread.ofVirtual().name("MRadmin-RoomAgent").unstarted(this::run);
        thread = t;
        t.start();
    }

    public void stop() {
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    private void run() {
        Thread self = Thread.currentThread();
        while (thread == self) {
            long started = System.currentTimeMillis();
            String url = plugin.waitingRoom().agentUrl();
            if (url != null) {
                poll(url);
                if (round++ % 3 == 0) {
                    events(url);
                }
            }
            try {
                Thread.sleep(Math.max(100, POLL_MILLIS - (System.currentTimeMillis() - started)));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void poll(String url) {
        try {
            String body = request(url, "/agent/health?after=" + health.newest());
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            List<HealthMonitor.Point> points = GSON.fromJson(o.get("points"), new TypeToken<List<HealthMonitor.Point>>() { }.getType());
            List<HealthMonitor.WorldInfo> worlds = GSON.fromJson(o.get("worlds"), new TypeToken<List<HealthMonitor.WorldInfo>>() { }.getType());
            double tps1m = o.has("tps1m") ? o.get("tps1m").getAsDouble() : 20;
            this.tps1m = tps1m;
            if (o.has("control") && o.get("control").isJsonObject()) {
                control = GSON.fromJson(o.get("control"), new TypeToken<java.util.Map<String, Object>>() { }.getType());
            }
            if (o.has("players") && o.get("players").isJsonArray()) {
                players = GSON.fromJson(o.get("players"), new TypeToken<List<java.util.Map<String, Object>>>() { }.getType());
                maxPlayers = o.has("maxPlayers") ? o.get("maxPlayers").getAsInt() : 0;
            }
            for (HealthMonitor.Point p : points) {
                health.accept(p, worlds == null ? List.of() : worlds, tps1m);
            }
            lastAnswer = System.currentTimeMillis();
            if (!lastProblem.isEmpty()) {
                plugin.getLogger().info("The waiting room's agent answers again (" + url + ").");
                lastProblem = "";
            }
        } catch (IOException | RuntimeException e) {
            String problem = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            // Once per kind of problem - a waiting room that restarts is normal, its log line is enough.
            if (!problem.equals(lastProblem)) {
                lastProblem = problem;
                plugin.getLogger().fine("Waiting room agent not readable (" + url + "): " + problem);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The waiting room's events since the last one into the log - once there, they stay there (keep-days). */
    private void events(String url) {
        try {
            if (lastEvent < 0) {
                var newest = plugin.eventLog().find(java.util.Set.of(SOURCE), null, 0, Long.MAX_VALUE, 1);
                lastEvent = newest.isEmpty() ? 0 : newest.getFirst().time();
            }
            JsonObject o = JsonParser.parseString(request(url, "/agent/events?after=" + lastEvent)).getAsJsonObject();
            for (var element : o.getAsJsonArray("events")) {
                JsonObject e = element.getAsJsonObject();
                long time = e.get("time").getAsLong();
                plugin.eventLog().add(SOURCE, e.get("event").getAsString(), e.get("text").getAsString(), e.get("color").getAsInt());
                lastEvent = Math.max(lastEvent, time);
            }
        } catch (IOException | RuntimeException e) {
            // next round
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A POST with a JSON body to the agent (a console command); returns the status code. */
    public int post(String path, String json) throws IOException, InterruptedException {
        return postFull(path, json).getKey();
    }

    /** A POST with a JSON body to the agent: status code and answer. */
    public java.util.Map.Entry<Integer, String> postFull(String path, String json) throws IOException, InterruptedException {
        String url = plugin.waitingRoom().agentUrl();
        if (url == null) {
            throw new IOException("no waiting room agent");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + plugin.waitingRoom().config().key())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return java.util.Map.entry(response.statusCode(), response.body());
    }

    /**
     * One request to the agent (path with query), the answer as JSON text - for what is only asked when someone looks
     * (system info, lag chunks). Throws when the waiting room is not known or does not answer.
     */
    public String get(String path) throws IOException, InterruptedException {
        String url = plugin.waitingRoom().agentUrl();
        if (url == null) {
            throw new IOException("no waiting room agent");
        }
        return request(url, path);
    }

    private String request(String url, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + plugin.waitingRoom().config().key()).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }
}
