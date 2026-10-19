package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.web.auth.WebAuth;
import at.marcelr.mradmin.web.auth.WebPermissions;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * The web interface: a small HTTP server built into Java, every request on its own virtual thread - nothing runs on
 * the server thread. It listens only locally by default; HTTPS and the domain come from the reverse proxy in front.
 * <p>
 * Serves the interface (web/ in the plugin JAR, built from the Svelte project) and the JSON API under /api. API
 * routes need a valid session and the permission mradmin.web unless they are public (sign-in, setup). Requests that
 * change something must carry the header {@code X-MRadmin: 1} and, if the browser sends one, the right Origin - a
 * foreign page cannot trigger actions with the visitor's cookie.
 */
public final class WebServer {

    /** Name of the session cookie. */
    public static final String COOKIE = "mradmin_session";
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; connect-src 'self'; font-src 'self'; object-src 'none'; "
            + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    /** A route handler; throw {@link Http.WebException} to answer with an error. */
    public interface Handler {
        void handle(Http http) throws Exception;
    }

    private record Route(String method, String[] parts, Handler handler, boolean open) {
    }

    private final MRAdmin plugin;
    private final WebAuth auth;
    private final WebPermissions permissions;
    private final List<Route> routes = new ArrayList<>();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private HttpServer server;
    private ExecutorService executor;
    private final WebEvents events = new WebEvents();
    private volatile Map<String, Object> snapshot = Map.of();
    private org.bukkit.scheduler.BukkitTask snapshotTask;
    private org.bukkit.scheduler.BukkitTask pruneTask;

    public WebServer(MRAdmin plugin, WebAuth auth, WebPermissions permissions) {
        this.plugin = plugin;
        this.auth = auth;
        this.permissions = permissions;
    }

    public WebAuth auth() {
        return auth;
    }

    public WebPermissions permissions() {
        return permissions;
    }

    public MRAdmin plugin() {
        return plugin;
    }

    /** A route that needs a session; {@code path} may contain {name} parts. */
    public void route(String method, String path, Handler handler) {
        routes.add(new Route(method, path.split("/"), handler, false));
    }

    /** A route anyone may call (sign-in, setup). */
    public void open(String method, String path, Handler handler) {
        routes.add(new Route(method, path.split("/"), handler, true));
    }

    public boolean running() {
        return server != null;
    }

    public WebEvents events() {
        return events;
    }

    /** The server state of the last second (TPS, players, maintenance, ticket counts) - see {@link WebSnapshot}. */
    public Map<String, Object> snapshot() {
        return snapshot;
    }

    /**
     * Runs {@code action} on the server thread and waits for the result - for the few things that must happen there
     * (Bukkit, the ticket data). Never call it from the server thread itself.
     */
    public <T> T sync(java.util.concurrent.Callable<T> action) throws Exception {
        try {
            return org.bukkit.Bukkit.getScheduler().callSyncMethod(plugin, action).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new Http.WebException(503, "busy");
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    // --------------------------------------------------------------- lifecycle

    /** Starts (or restarts) with the current settings; does nothing if web.enabled is false. */
    public void start() {
        stop();
        Settings.WebSettings s = plugin.settings().web();
        if (!s.enabled()) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(s.bind(), s.port()), 0);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            snapshotTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> snapshot = WebSnapshot.build(plugin), 1L, 20L);
            // Retention period of the log (web.privacy.audit-retention): now and every 6 hours.
            pruneTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.storage().pruneAudit(
                    System.currentTimeMillis() - plugin.settings().web().auditRetention()), 20L, 6 * 72_000L);
            plugin.getLogger().info("Web interface listening on " + s.bind() + ":" + s.port()
                    + (s.publicUrl().isEmpty() ? " (set web.public-url to the address of your reverse proxy)" : " for " + s.publicUrl()) + ".");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("Could not start the web interface on " + s.bind() + ":" + s.port() + ": " + e.getMessage());
            server = null;
        }
    }

    public void stop() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
        if (pruneTask != null) {
            pruneTask.cancel();
            pruneTask = null;
        }
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    // ---------------------------------------------------------------- requests

    private void handle(HttpExchange exchange) {
        Settings.WebSettings s = plugin.settings().web();
        Http http = new Http(exchange, s.trustedProxies(), s.maskIp());
        try {
            securityHeaders(exchange);
            String path = http.path();
            if (path.startsWith("/api/")) {
                api(http, s);
            } else if (http.method().equals("GET") || http.method().equals("HEAD")) {
                staticFile(http, path);
            } else {
                http.error(405, "method");
            }
        } catch (Http.WebException e) {
            answer(http, e.status, e.key);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Web request " + http.method() + " " + http.path() + " failed", e);
            answer(http, 500, "server");
        } finally {
            exchange.close();
        }
    }

    private void answer(Http http, int status, String key) {
        try {
            http.error(status, key);
        } catch (IOException ignored) {
            // the browser is gone
        }
    }

    private void api(Http http, Settings.WebSettings s) throws Exception {
        boolean change = !http.method().equals("GET") && !http.method().equals("HEAD");
        if (!allow("api:" + http.ip(), 900)) {
            throw new Http.WebException(429, "rate-limit");
        }
        if (change && (!"1".equals(http.header("X-MRadmin")) || !sameOrigin(http, s))) {
            throw new Http.WebException(403, "origin");
        }
        for (Route route : routes) {
            Map<String, String> params = match(route, http);
            if (params == null) {
                continue;
            }
            http.params(params);
            if (!route.open()) {
                WebAuth.Session session = auth.session(http.cookie(COOKIE), http.storedIp());
                if (session == null) {
                    throw new Http.WebException(401, "signed-out");
                }
                if (!permissions.has(session.account(), WebPermissions.PERM_WEB)) {
                    // Lost the web permission: all sessions of this account end.
                    auth.revokeAll(session.account(), null);
                    throw new Http.WebException(403, "no-web-permission");
                }
                http.session = session;
                http.account = auth.account(session.account());
            }
            route.handler().handle(http);
            if (!http.answered()) {
                http.json(200, Map.of("ok", true));
            }
            return;
        }
        throw new Http.WebException(404, "not-found");
    }

    private static Map<String, String> match(Route route, Http http) {
        if (!route.method().equals(http.method())) {
            return null;
        }
        String[] parts = http.path().split("/");
        if (parts.length != route.parts().length) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < parts.length; i++) {
            String expected = route.parts()[i];
            if (expected.startsWith("{") && expected.endsWith("}")) {
                params.put(expected.substring(1, expected.length() - 1), parts[i]);
            } else if (!expected.equals(parts[i])) {
                return null;
            }
        }
        return params;
    }

    /** No Origin header (old browsers, curl) is fine thanks to the custom header; a foreign Origin is not. */
    private static boolean sameOrigin(Http http, Settings.WebSettings s) {
        String origin = http.header("Origin");
        if (origin == null) {
            return true;
        }
        if (!s.publicUrl().isEmpty()) {
            URI url = URI.create(s.publicUrl());
            return origin.equalsIgnoreCase(url.getScheme() + "://" + url.getRawAuthority());
        }
        String host = http.header("Host");
        return host != null && (origin.equalsIgnoreCase("http://" + host) || origin.equalsIgnoreCase("https://" + host));
    }

    private static void securityHeaders(HttpExchange exchange) {
        var h = exchange.getResponseHeaders();
        h.set("Content-Security-Policy", CSP);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        h.set("Cross-Origin-Opener-Policy", "same-origin");
    }

    // ------------------------------------------------------------ static files

    /** web/ in the JAR; unknown paths without a file extension get index.html (the interface routes itself). */
    private void staticFile(Http http, String path) throws IOException {
        String file = path.equals("/") ? "index.html" : path.substring(1);
        if (file.contains("..") || file.contains("\\")) {
            throw new Http.WebException(404, "not-found");
        }
        byte[] data = resource("web/" + file);
        if (data == null && !file.contains(".")) {
            file = "index.html";
            data = resource("web/index.html");
        }
        if (data == null) {
            if (file.equals("index.html")) {
                // Built without the interface (e.g. a test build) - say so instead of a blank page.
                http.send(503, "text/plain; charset=utf-8",
                        "MRadmin web interface is not included in this build.".getBytes(StandardCharsets.UTF_8), "no-store");
                return;
            }
            throw new Http.WebException(404, "not-found");
        }
        // Files under assets/ have a hash in their name - they can be cached for good.
        String cache = file.startsWith("assets/") ? "public, max-age=31536000, immutable" : "no-cache";
        http.send(200, type(file), data, cache);
    }

    private byte[] resource(String name) throws IOException {
        try (InputStream in = plugin.getResource(name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static String type(String file) {
        String ext = file.substring(file.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "html" -> "text/html; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "json", "webmanifest" -> "application/manifest+json";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "ico" -> "image/x-icon";
            case "woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
    }

    // ------------------------------------------------------------- rate limits

    /** A simple bucket: {@code perMinute} requests, refilled continuously. */
    private static final class Bucket {
        double tokens;
        long last;
    }

    /** Whether one more request under {@code key} is allowed (e.g. "login:1.2.3.4"). */
    public boolean allow(String key, int perMinute) {
        long now = System.nanoTime();
        Bucket bucket = buckets.computeIfAbsent(key, k -> {
            Bucket b = new Bucket();
            b.tokens = perMinute;
            b.last = now;
            return b;
        });
        synchronized (bucket) {
            bucket.tokens = Math.min(perMinute, bucket.tokens + (now - bucket.last) / 60e9 * perMinute);
            bucket.last = now;
            if (bucket.tokens < 1) {
                return false;
            }
            bucket.tokens--;
        }
        if (buckets.size() > 10_000) {
            buckets.clear();
        }
        return true;
    }
}
