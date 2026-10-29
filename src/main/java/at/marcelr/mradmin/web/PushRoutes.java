package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.web.push.PushService;
import at.marcelr.mradmin.web.push.WebPush;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The member's own push notifications.
 * <pre>
 *  GET  /api/push                          server key, kinds the member may get (with their choice), their devices
 *  POST /api/push/subscribe   {endpoint, p256dh, auth}   this browser (bound to this session)
 *  POST /api/push/unsubscribe {endpoint}
 *  POST /api/push/choose      {kind, on}
 *  POST /api/push/test                     a test notification to all own devices
 * </pre>
 */
public final class PushRoutes {

    private final WebServer server;
    private final MRAdmin plugin;

    private PushRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        PushRoutes r = new PushRoutes(server);
        server.route("GET", "/api/push", r::state);
        server.route("POST", "/api/push/subscribe", r::subscribe);
        server.route("POST", "/api/push/unsubscribe", r::unsubscribe);
        server.route("POST", "/api/push/choose", r::choose);
        server.route("POST", "/api/push/test", r::test);
    }

    private PushService push() throws Http.WebException {
        PushService push = server.push();
        if (!push.available()) {
            throw new Http.WebException(503, "push-unavailable");
        }
        return push;
    }

    private void state(Http http) throws Exception {
        PushService push = push();
        List<Map<String, Object>> devices = new ArrayList<>();
        for (PushService.Subscription s : push.devices(http.account().id())) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("endpoint", s.endpoint());
            d.put("device", s.device());
            d.put("created", s.created());
            d.put("current", s.session().equals(http.session().id()));
            devices.add(d);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", push.publicKey());
        result.put("kinds", push.choices(http.account().id()));
        // Names of the modules' kinds (MRadmin's own come from the web interface's texts).
        result.put("labels", push.labels(http.account().id()));
        result.put("devices", devices);
        http.json(200, result);
    }

    private void subscribe(Http http) throws Exception {
        String endpoint = http.string("endpoint");
        if (endpoint == null) {
            throw new Http.WebException(400, "push-invalid");
        }
        String error = push().subscribe(http.account().id(), http.session().id(), endpoint, http.string("p256dh"),
                http.string("auth"), device(http.header("User-Agent")));
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        http.json(200, Map.of());
    }

    private void unsubscribe(Http http) throws Exception {
        String endpoint = http.string("endpoint");
        if (endpoint != null) {
            push().unsubscribe(http.account().id(), endpoint);
        }
        http.json(200, Map.of());
    }

    private void choose(Http http) throws Exception {
        push().choose(http.account().id(), String.valueOf(http.string("kind")), "true".equals(http.string("on")));
        http.json(200, Map.of());
    }

    private void test(Http http) throws Exception {
        if (!server.allow("push-test:" + http.account().id(), 5)) {
            throw new Http.WebException(429, "rate-limit");
        }
        List<PushService.TestResult> results = push().test(http.account().id(),
                plugin.messages().plain("push-test-title"), plugin.messages().plain("push-test-body"));
        // Refused pushes with the push service's answer (Apple e.g. "BadJwtToken"), so the cause shows in the web UI.
        List<Map<String, Object>> failed = new ArrayList<>();
        for (PushService.TestResult r : results) {
            if (r.result().outcome() != WebPush.Outcome.SENT) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("device", r.device());
                f.put("status", r.result().status());
                f.put("reason", r.result().reason());
                failed.add(f);
            }
        }
        http.json(200, Map.of("devices", results.size(), "failed", failed));
    }

    /** "Chrome on Windows" from the user agent - only for telling devices apart. */
    static String device(String agent) {
        if (agent == null) {
            return "?";
        }
        String browser = agent.contains("Edg/") ? "Edge" : agent.contains("Firefox/") ? "Firefox"
                : agent.contains("Chrome/") ? "Chrome" : agent.contains("Safari/") ? "Safari" : "Browser";
        String os = agent.contains("iPhone") ? "iPhone" : agent.contains("iPad") ? "iPad"
                : agent.contains("Android") ? "Android" : agent.contains("Windows") ? "Windows"
                : agent.contains("Mac OS X") ? "macOS" : agent.contains("Linux") ? "Linux" : "";
        return os.isEmpty() ? browser : browser + " · " + os;
    }
}
