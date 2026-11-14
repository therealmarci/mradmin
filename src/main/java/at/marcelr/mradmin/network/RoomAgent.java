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
 * lag alarm). System info and lag chunks are only asked when someone looks (passed through, {@link #get}).
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

    public RoomAgent(MRAdmin plugin) {
        this.plugin = plugin;
        this.health = new HealthMonitor(plugin, "waitroom-health");
    }

    /** The recorded health of the waiting room server. */
    public HealthMonitor health() {
        return health;
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
