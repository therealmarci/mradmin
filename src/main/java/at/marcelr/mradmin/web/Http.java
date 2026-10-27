package at.marcelr.mradmin.web;

import at.marcelr.mradmin.web.auth.WebAuth;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One request and its answer. Only used on the web thread that handles it. */
public final class Http {

    /** Bodies bigger than this are refused (nothing in the web interface sends more). */
    static final int MAX_BODY = 64 * 1024;
    private static final Gson GSON = new Gson();

    private final HttpExchange exchange;
    private final String ip;
    private final boolean maskIp;
    private final Map<String, String> params = new HashMap<>();
    private JsonObject body;
    private boolean answered;

    /** Set by the server for requests with a valid session. */
    WebAuth.Session session;
    WebAuth.Account account;

    Http(HttpExchange exchange, Set<String> trustedProxies, boolean maskIp) {
        this.exchange = exchange;
        this.ip = clientIp(exchange, trustedProxies);
        this.maskIp = maskIp;
    }

    /**
     * The visitor's address. X-Forwarded-For is only believed when the request comes from a trusted proxy; then the
     * last address in it that is not a trusted proxy itself is the visitor (the proxy appends, a client may fake the
     * beginning).
     */
    private static String clientIp(HttpExchange exchange, Set<String> trusted) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        String direct = remote == null ? "?" : remote.getAddress().getHostAddress();
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded == null || !trusted.contains(direct)) {
            return direct;
        }
        List<String> hops = List.of(forwarded.split(","));
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i).trim();
            if (!hop.isEmpty() && !trusted.contains(hop)) {
                return hop;
            }
        }
        return direct;
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    public String path() {
        return exchange.getRequestURI().getPath();
    }

    public String ip() {
        return ip;
    }

    /**
     * The address as it is stored (log, sessions): with web.privacy.mask-ip the last part is zeroed - 1.2.3.4 becomes
     * 1.2.3.0, an IPv6 address keeps its first three blocks. Rate limits always use the full address.
     */
    public String storedIp() {
        return maskIp ? mask(ip) : ip;
    }

    static String mask(String ip) {
        if (ip.contains(".") && !ip.contains(":")) {
            int last = ip.lastIndexOf('.');
            return last < 0 ? ip : ip.substring(0, last) + ".0";
        }
        String[] parts = ip.split(":");
        return parts.length < 3 ? ip : parts[0] + ":" + parts[1] + ":" + parts[2] + "::";
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public WebAuth.Session session() {
        return session;
    }

    public WebAuth.Account account() {
        return account;
    }

    /** A {name} part of the route, e.g. the id in /api/sessions/{id}. */
    public String param(String name) {
        return params.get(name);
    }

    void params(Map<String, String> values) {
        params.putAll(values);
    }

    /** The query string as name -> value (first value wins), e.g. {@code ?id=3&filter=all}. */
    public Map<String, String> query() {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            if (!name.isEmpty()) {
                values.putIfAbsent(name, value);
            }
        }
        return values;
    }

    /**
     * The JSON body as plain values: strings, booleans, whole numbers as Long, other numbers as Double. Nested objects
     * and arrays are left out.
     */
    public Map<String, Object> bodyValues() throws IOException {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : body().entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }
            var primitive = entry.getValue().getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                values.put(entry.getKey(), primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                double number = primitive.getAsDouble();
                values.put(entry.getKey(), number == Math.rint(number) && Math.abs(number) < 9.0E15
                        ? (Object) (long) number : number);
            } else {
                values.put(entry.getKey(), primitive.getAsString());
            }
        }
        return values;
    }

    public String cookie(String name) {
        String header = header("Cookie");
        if (header == null) {
            return null;
        }
        for (String part : header.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).trim().equals(name)) {
                return URLDecoder.decode(part.substring(eq + 1).trim(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** The JSON body (empty object if there is none or it is not JSON). */
    public JsonObject body() throws IOException {
        if (body == null) {
            try (InputStream in = exchange.getRequestBody()) {
                byte[] data = in.readNBytes(MAX_BODY + 1);
                if (data.length > MAX_BODY) {
                    throw new WebException(413, "too-large");
                }
                JsonElement parsed = data.length == 0 ? null : JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
                body = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            } catch (com.google.gson.JsonParseException e) {
                body = new JsonObject();
            }
        }
        return body;
    }

    /** A string field of the body, null if missing. */
    public String string(String field) throws IOException {
        JsonElement value = body().get(field);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    // ----------------------------------------------------------------- answers

    public void setCookie(String name, String value, long maxAgeSeconds, boolean secure) {
        exchange.getResponseHeaders().add("Set-Cookie", name + "=" + value + "; Path=/; HttpOnly; SameSite=Strict"
                + (secure ? "; Secure" : "") + "; Max-Age=" + maxAgeSeconds);
    }

    public void json(int status, Object value) throws IOException {
        send(status, "application/json; charset=utf-8", GSON.toJson(value).getBytes(StandardCharsets.UTF_8), "no-store");
    }

    /** An error as {"error": "<key>"} - the web interface shows the matching text. */
    public void error(int status, String key) throws IOException {
        json(status, Map.of("error", key));
    }

    void send(int status, String type, byte[] data, String cache) throws IOException {
        if (answered) {
            return;
        }
        answered = true;
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type);
        headers.set("Cache-Control", cache);
        if (method().equals("HEAD")) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
        if (data.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data);
            }
        }
    }

    /** Starts an endless answer (Server-Sent Events); the caller writes to it until the browser goes away. */
    public OutputStream stream(String type) throws IOException {
        answered = true;
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type);
        headers.set("Cache-Control", "no-store");
        // nginx would otherwise collect the events and send them much later.
        headers.set("X-Accel-Buffering", "no");
        exchange.sendResponseHeaders(200, 0);
        return exchange.getResponseBody();
    }

    boolean answered() {
        return answered;
    }

    HttpExchange exchange() {
        return exchange;
    }

    /** Ends a request early with an error status (caught by the server). */
    public static final class WebException extends IOException {

        final int status;
        final String key;

        public WebException(int status, String key) {
            super(key);
            this.status = status;
            this.key = key;
        }
    }
}
